package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import org.checkerframework.common.returnsreceiver.qual.This;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class DijkstraScoreFactory {

    private static Dijkstra<Integer> dijkstra;

    //lazy singleton
    public Dijkstra syncBuild(Board board){
        if(dijkstra == null){
          //only construct once
            synchronized (this.getClass()){
                if(dijkstra == null){
                    build(board);
                }
            }
        }
        return dijkstra;
    }

    //factory design pattern
    private void build(Board board){
        ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
        Set<Integer> nodes = graph.nodes(); //a set of all points
        Dijkstra<Integer> directNet = new Dijkstra<>(nodes.size());
        nodes.stream().forEach(directNet::addVertex);
        for (Integer node : nodes) {
            Set<Integer> destinations = graph.adjacentNodes(node);
            for (Integer destination : destinations) {
                int weight = Integer.MAX_VALUE;
                //get the least weight
                for (ScotlandYard.Transport t : Objects.requireNonNull(graph.edgeValueOrDefault(node, destination, ImmutableSet.of()))) {
                     //choose the cheapest tickets
                    int temp = getWeight(t.requiredTicket());
                    weight = weight > temp ? temp : weight;
                }
                //construct edge
                directNet.addEdge(node, destination, weight);
            }
        }
        this.dijkstra = directNet;
    }


    //Number for tickets are assigned by the default value,  decide the value in terms of scarcity
    private int getWeight(ScotlandYard.Ticket requiredTicket) {
        switch (requiredTicket){
            case TAXI:
                return 1;
            case BUS:
                return 2;
            case UNDERGROUND:
                return 3;
        }
        return 1;
    }

    class Dijkstra<V> {
        private List<V> vertexList;

       // Coordinates are positions, values are distances
       //construct from build
        private int[][] edgeMatrix;

        //the amount of nodes, equals to the size of vertexList
        private int vertices;


       // initial state  all vertices are unvisited and all edges have infinite weight

        public Dijkstra(int size) {
            vertexList = new ArrayList<>();
            edgeMatrix = new int[size][size];
            this.vertices = 0;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (i == j) {
                        this.edgeMatrix[i][j] = 0;
                    } else {
                        this.edgeMatrix[i][j] = Integer.MAX_VALUE;
                    }
                }
            }
        }

        public void addVertex(V v) {
            this.vertexList.add(v);
            this.vertices++;
        }

        public void addEdge(V from, V to, int weight) {
            int i = this.vertexList.indexOf(from);
            int j = this.vertexList.indexOf(to);
            this.edgeMatrix[i][j] = weight;
        }

        public int dijkstra(V v0, V vi) {
            //calculate the distance, the point which near to mrx
            //can be improved
            ArrayList<Integer> listU = new ArrayList<>();

            int[] dist = new int[this.vertices]; //distance from all points to the start
            int[] path = new int[this.vertices];


            int start = this.vertexList.indexOf(v0);
            int end = this.vertexList.indexOf(vi);

            //iterate the vertices util the start vertex
            for (int i = 0; i < this.vertices; i++) {
                if (i == start) {
                    continue;
                }
                listU.add(i);
            }
            //calculate the start’s next nodes and their distances
            for (int i = 0; i < this.vertices; i++) {
                dist[i] = this.edgeMatrix[start][i];//weight
                if (this.edgeMatrix[start][i] == Integer.MAX_VALUE) {
                    path[i] = -1;
                } else {
                    path[i] = start;
                }
            }

            int minIndex;
            do {
                //get the min distance node index in the start's next nodes
                minIndex = listU.get(0);
                for (int i = 1; i < listU.size(); i++) {
                    if (dist[listU.get(i)] < dist[minIndex]) {
                        minIndex = listU.get(i);
                    }
                }
                //remove it
                listU.remove((Integer) minIndex);
                // update distances of all next nodes
                for (int i = 0; i < this.vertices; i++) {
                    //get next nodes of the minIndex node
                    if (this.edgeMatrix[minIndex][i] != 0 && this.edgeMatrix[minIndex][i] < Integer.MAX_VALUE) {
                        //check if path through minIndex to reach that vertex has a lower total distance
                        if (this.edgeMatrix[minIndex][i] + dist[minIndex] < dist[i]) {
                            dist[i] = this.edgeMatrix[minIndex][i] + dist[minIndex];
                            path[i] = minIndex;
                        }
                    }
                }
            } while (minIndex != end && !listU.isEmpty()); // reaches the destination  or listU empty
            return dist[end];
        }

        /**
         * the mrX move score is the least distance to the detectives
         * @param destination
         * @param board
         * @return
         */


        public Integer score(int destination, Board board) {
            List<Piece> detectives = board.getPlayers().stream().filter(e -> e.isDetective()).collect(Collectors.toList());
            int score = Integer.MAX_VALUE;
            for (Piece detective : detectives) {
                int location = board.getDetectiveLocation((Piece.Detective) detective).get();
                int tempScore = dijkstra.dijkstra(location, destination);
                //determine which detective near mrx
                score = tempScore < score ? tempScore : score;
            }
            return score;
        }


    }
}
