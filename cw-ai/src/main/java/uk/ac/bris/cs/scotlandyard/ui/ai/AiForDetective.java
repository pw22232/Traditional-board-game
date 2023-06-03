package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class AiForDetective implements Ai {

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

	@Nonnull @Override public String name() { return "AiForDetective"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		return getBestMove(moves, board);
	}

	private Move getBestMove(ImmutableList<Move> moves, Board board) {
		int score = -1;
		List<Move> possibleMoves = new ArrayList<>();
		ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
		for (int i = 0; i < moves.size(); i++) {
			int destination = moves.get(i).accept(visitor);
			int tempScore = graph.adjacentNodes(destination).size();//freedom
			if(tempScore > score){
				score = tempScore;
				possibleMoves = new ArrayList<>();
				possibleMoves.add(moves.get(i));
			}else if(tempScore == score){
				possibleMoves.add(moves.get(i));
			}
		}
		Random rand = new Random();
		return possibleMoves.get(rand.nextInt(possibleMoves.size()));
	}
}
