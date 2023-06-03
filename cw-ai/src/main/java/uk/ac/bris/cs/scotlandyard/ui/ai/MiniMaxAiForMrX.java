package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class MiniMaxAiForMrX implements Ai {

    /**
     * Apply the move to a given board using the advance method
     * @param board
     * @param move
     * @return
     */
    private Board makeMove (Board board, Move move){
        return ((Board.GameState) board).advance(move);
    }

    /**
     * Check if MrX is in the remaining player
     * @param board
     * @return
     */
    private boolean isMrXRemaining(Board board) {
        for (Move move : board.getAvailableMoves()) {
            if (move.commencedBy().isMrX()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apply all the detective moves to the board
     * @param board
     * @return
     */
    private List<Board> applyAllDetectiveMoves (Board board){
        if (isMrXRemaining(board)) {
            return List.of(board);
        }
        List<Board> boards = new ArrayList<>();
        for (Move move : board.getAvailableMoves()){
            boards.addAll(applyAllDetectiveMoves(makeMove(board, move)));
        }
        return boards;
    }

    /**
     * Min-Max algorithm for the AI, sort through the possible moves and score them
     * @param board
     * @param depth
     * @param isMrX
     * @param mrXLocation
     * @param alpha
     * @param beta
     * @return
     */
    private int minimax(Board board, int depth, boolean isMrX, int mrXLocation, int alpha, int beta){
        if (!board.getWinner().isEmpty()){
            return 0;
        }
        if (depth == 0){
            return score(mrXLocation, board);
        }
        if (isMrX){
            //mrx's round
            int maxEval = (int)Double.NEGATIVE_INFINITY;
            for (Move move : board.getAvailableMoves()){
                Board moveMade = makeMove(board, move);
                int eval = minimax(moveMade, depth-1, false, move.accept(visitor), alpha, beta);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            //detectives' round
            int minEval = (int)Double.POSITIVE_INFINITY;
            for (Board boardDetective : applyAllDetectiveMoves(board)){
                int eval = minimax(boardDetective, depth-1, true, mrXLocation, alpha, beta);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;

        }
    }

    /**
     * score method
     * @param mrXLocation
     * @param board
     * @return
     */
    private int score(int mrXLocation, Board board) {
        ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
        int freedom = graph.adjacentNodes(mrXLocation).size();
        int distance = dijkstra.score(mrXLocation, board);
        int rate;
        if(distance <= 5){
            rate = 4;
        }else if(distance > 5 && distance <= 10){
            rate = 2;
        }else {
            rate = 1;
        }
        //The closer detectives get, the farther mrx run
        return freedom + distance * rate;
    }



    //entrance method

    private Move applyMiniMax(Board board, int depth){
        int maxEval = (int)Double.NEGATIVE_INFINITY;
        int alpha = (int)Double.NEGATIVE_INFINITY;
        int beta = (int)Double.POSITIVE_INFINITY;

        List<Move> possibleMoves = new ArrayList<>();
        ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
        int bestIndex = -1;

        ImmutableList<Move> moves = board.getAvailableMoves().asList();
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            makeMove(board, move);
            int eval = minimax(board, depth - 1, false, move.accept(visitor), alpha, beta);
            if (eval > maxEval || (eval == maxEval && graph.adjacentNodes(move.accept(visitor)).size() > graph.adjacentNodes(moves.get(bestIndex).accept(visitor)).size())){
                maxEval = eval;
                bestIndex = i;
                possibleMoves = new ArrayList<>();
                possibleMoves.add(move);
            } else if (eval == maxEval && graph.adjacentNodes(move.accept(visitor)).size() > graph.adjacentNodes(moves.get(bestIndex).accept(visitor)).size()){
                possibleMoves.add(move);
            }
        }
        Random rand = new Random();
        return possibleMoves.get(rand.nextInt(possibleMoves.size()));
    }

    @Nonnull
    @Override public String name() { return "MiniMaxAiForMrX"; }

    //Dijkstra metrics
    private DijkstraScoreFactory.Dijkstra dijkstra;

    //move visitor
    private Move.Visitor<Integer> visitor = new Move.Visitor<>() {
        @Override
        public Integer visit(Move.SingleMove move) {
            return move.destination;
        }

        @Override
        public Integer visit(Move.DoubleMove move) {
            return move.destination2;
        }
    };

    @Nonnull
    @Override
    public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        dijkstra = new DijkstraScoreFactory().syncBuild(board);
        Move move = applyMiniMax(board, 2);
        return move;
    }
}

