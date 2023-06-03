package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.ac.bris.cs.scotlandyard.model.LogEntry.hidden;
import static uk.ac.bris.cs.scotlandyard.model.LogEntry.reveal;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class  MyGameStateFactory implements Factory<GameState> {

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(Piece.MrX.MRX), ImmutableList.of(), mrX, detectives);
	}

	private class MyGameState implements GameState {

		//setup model
		private GameSetup setup;

		//mrX model
		private Player mrX;

		//detectives models
		private List<Player> detectives;

		//travelLog model
		private ImmutableList<LogEntry> travelLog;

		//the winner data, to determine whether the games is over
		private ImmutableSet<Piece> winner;

		//the available player to move
		private ImmutableSet<Piece> availablePlayer;

		//available move actions
		private final ImmutableSet<Move> moves;
		//judge type of the move
		Move.Visitor<Boolean> doubleCheck = new Move.Visitor<>() {
			@Override
			public Boolean visit(Move.SingleMove move) {
				return false;
			}

			@Override
			public Boolean visit(Move.DoubleMove move) {
				for (var t : move.tickets()) {
					if (Objects.equals(t, ScotlandYard.Ticket.DOUBLE)) {
						return true;
					}
				}
				return false;
			}
		};

		Move.Visitor<Integer> destination = new Move.Visitor<>() {
			@Override
			public Integer visit(Move.SingleMove singleMove) { return singleMove.destination; }
			@Override
			public Integer visit(Move.DoubleMove doubleMove) { return doubleMove.destination2; }
		};

		Move.Visitor<Integer> destination1 = new Move.Visitor<>() {
			@Override
			public Integer visit(Move.SingleMove singleMove) { return singleMove.destination; }
			@Override
			public Integer visit(Move.DoubleMove doubleMove) { return doubleMove.destination1; }
		};

		Move.Visitor<ScotlandYard.Ticket> ticket1 = new Move.Visitor<>() {
			@Override
			public ScotlandYard.Ticket visit(Move.SingleMove singleMove) { return singleMove.ticket; }
			@Override
			public ScotlandYard.Ticket visit(Move.DoubleMove doubleMove) { return doubleMove.ticket1; }
		};

		Move.Visitor<ScotlandYard.Ticket> ticket2 = new Move.Visitor<>() {
			@Override
			public ScotlandYard.Ticket visit(Move.SingleMove singleMove) { return null; }
			@Override
			public ScotlandYard.Ticket visit(Move.DoubleMove doubleMove) { return doubleMove.ticket2; }
		};

		public MyGameState(GameSetup setup, ImmutableSet<Piece> availablePlayer, ImmutableList<LogEntry> travelLog, Player mrX, List<Player> detectives) {
			this.setup = setup;
			this.availablePlayer = availablePlayer;
			this.travelLog = travelLog;
			this.mrX = mrX;
			this.detectives = detectives;
			//check the arguments
			argusCheck(setup, mrX, detectives);
			this.winner = ImmutableSet.of();
			this.moves = getAvailableMoves();
			this.winner = getWinner();
		}

		private void argusCheck(GameSetup setup, Player mrX, List<Player> detectives) {
			if (mrX == null || detectives.isEmpty()) {
				throw new NullPointerException();
			}
			if (!mrX.isMrX() || setup.moves.isEmpty() || setup.graph.nodes().size() == 0) {
				throw new IllegalArgumentException();
			}
			for (Player i : detectives) {
				for (Player j : detectives) {
					if (i.piece() == j.piece() && i != j) {
						throw new IllegalArgumentException();
					}
					if (i.location() == j.location() && i != j) {
						throw new IllegalArgumentException();
					}
				}
				if (i.has(ScotlandYard.Ticket.SECRET) || i.has(ScotlandYard.Ticket.DOUBLE) || !i.isDetective()) {
					throw new IllegalArgumentException();
				}
			}
		}

		@Nonnull
		@Override
		public GameSetup getSetup() {
			return setup;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			HashSet<Piece> players = new HashSet<>();
			players.add(mrX.piece());
			players.addAll(detectives.stream().map(Player::piece).collect(Collectors.toSet()));
			return ImmutableSet.copyOf(players);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			Optional<Player> first = detectives.stream().filter(e -> e.piece() == detective).findFirst();
			if (first.isEmpty()) {
				return Optional.empty();
			} else {
				return Optional.of(first.get().location());
			}
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			TicketBoard ticketBoard = ticket -> {
				if (piece.isMrX()) {
					return mrX.tickets().getOrDefault(ticket, 0);
				}
				for (Player detective : detectives) {
					if (detective.piece() == piece) {
						return detective.tickets().getOrDefault(ticket, 0);
					}
				}
				return 0;
			};
			Optional<TicketBoard> board = Optional.of(ticketBoard);
			if (piece.isMrX() || detectives.stream().map(Player::piece).anyMatch(e -> e == piece)) {
				return board;
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return travelLog;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			HashSet<Piece> winner = new HashSet<>();

			//mrX was caught by any detective
			if(detectives.stream().map(Player::location).anyMatch(e -> e == mrX.location())){
				winner.addAll(detectives.stream().map(Player::piece).collect(Collectors.toSet()));
				return ImmutableSet.copyOf(winner);
			}

			boolean detectivesLost = true;
			for (Player detective : detectives) {
				if (Stream.of(ScotlandYard.Ticket.TAXI, ScotlandYard.Ticket.BUS, ScotlandYard.Ticket.UNDERGROUND).anyMatch(ticket -> detective.hasAtLeast(ticket, 1))) {
					detectivesLost = false;
					break;
				}
			}
			if (detectivesLost || availablePlayer.isEmpty()) {
				winner.add(Piece.MrX.MRX);
			}

			if (availablePlayer.contains(Piece.MrX.MRX) && moves.isEmpty()) {
				winner.addAll(detectives.stream().map(Player::piece).collect(Collectors.toSet()));
			}
			return ImmutableSet.copyOf(winner);
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			if(!winner.isEmpty()){
				return ImmutableSet.of();
			}
			HashSet<Move> moves = new HashSet<>();
			if (availablePlayer.contains(Piece.MrX.MRX)){
				moves.addAll(singleMoves(setup, detectives, mrX));
				moves.addAll(doubleMoves(setup, detectives, mrX));
			} else {
				detectives.stream()
						.filter(e -> availablePlayer.contains(e.piece()))
						.map(e -> singleMoves(setup, detectives, e))
						.forEach(moves::addAll);
			}
			return ImmutableSet.copyOf(moves);
		}

		@Nonnull
		@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) {
				throw new IllegalArgumentException("illegal move: " + move);
			}
			updateTickets(move);
			updateLocationAndTravelLog(move);
			updateAvailablePlayers(move);
			return new MyGameState(setup, availablePlayer, travelLog, mrX, detectives);
		}

		private void updateLocationAndTravelLog(Move move) {
			Piece piece = move.commencedBy();
			if (!piece.isMrX()) {
				ArrayList<Player> tempPlayers = new ArrayList<>();
				detectives.stream().map(e -> e.piece() == piece ? e.at(move.accept(destination)) : e)
						.forEach(tempPlayers::add);
				detectives = List.copyOf(tempPlayers);
			} else {
				mrX = mrX.at(move.accept(destination));
				addTravelLog(move);
			}
		}

		//add travel log
		private void addTravelLog(Move move) {
			ArrayList<LogEntry> updateLog = new ArrayList<>(travelLog);
			boolean isreveal = false, isrevealnext = false; //secret
			var isCurrentRound = 0;

			for (boolean i : setup.moves) {
				isCurrentRound++;
				if (isCurrentRound == travelLog.size() + 1) {
					isreveal = i;
				} else if (isCurrentRound == travelLog.size() + 2) {
					isrevealnext = i;
				}
			}
			Boolean isDouble = move.accept(doubleCheck);
			// every possible log entry given any move and round
			if (isDouble && isreveal && isrevealnext) {
				updateLog.add(reveal(move.accept(ticket1), move.accept(destination1)));
				updateLog.add(reveal(move.accept(ticket2), move.accept(destination)));
			} else if (isDouble && isreveal) {
				updateLog.add(reveal(move.accept(ticket1), move.accept(destination1)));
				updateLog.add(hidden(move.accept(ticket2)));
			} else if (isDouble && isrevealnext) {
				updateLog.add(hidden(move.accept(ticket1)));
				updateLog.add(reveal(move.accept(ticket2), move.accept(destination)));
			} else if (isDouble) {
				updateLog.add(hidden(move.accept(ticket1)));
				updateLog.add(hidden(move.accept(ticket2)));
			} else if (isreveal) {
				updateLog.add(reveal(move.accept(ticket1), move.accept(destination)));
			} else {
				updateLog.add(hidden(move.accept(ticket1)));
			}
			travelLog = ImmutableList.copyOf(updateLog);
		}

		private void updateTickets(Move move) {
			Piece piece = move.commencedBy();
			if (!piece.isMrX()) {
				ArrayList<Player> tempPlayers = new ArrayList<>();
				for (Player player : detectives) {
					if (piece == player.piece()) {
						tempPlayers.add(player.use(move.tickets()));
						mrX = mrX.give(move.tickets());
					} else {
						tempPlayers.add(player);
					}
				}
				detectives = List.copyOf(tempPlayers);
			} else {
				mrX = mrX.use(move.tickets());
			}
		}

		private void updateAvailablePlayers(Move move){
			HashSet<Piece> tempDetectives = new HashSet<>();
			if (move.commencedBy().isMrX()) {
				detectives.stream().map(Player::piece).forEach(tempDetectives::add);
			} else {
				availablePlayer.stream().filter(e -> !Objects.equals(move.commencedBy(), e))
						.forEachOrdered(e -> detectives.stream()
								.filter(det -> Objects.equals(det.piece(), e)
										&& Stream.of(ScotlandYard.Ticket.TAXI, ScotlandYard.Ticket.BUS, ScotlandYard.Ticket.UNDERGROUND)
										.anyMatch(ticket -> det.hasAtLeast(ticket, 1)))
								.map(det -> e)
								.forEachOrdered(tempDetectives::add));
			}
			if (!tempDetectives.isEmpty()) {
				availablePlayer = ImmutableSet.copyOf(tempDetectives);
			} else if (travelLog.size() >= setup.moves.size()) {
				availablePlayer = ImmutableSet.of();
			} else {
				availablePlayer = ImmutableSet.of(Piece.MrX.MRX);
			}
		}

		private ImmutableSet<Move.SingleMove> singleMoves(GameSetup setup, List<Player> detectives, Player player) {
			int start = player.location();
			List<Move.SingleMove> singleMoves = new ArrayList<>();
			for (int destination : setup.graph.adjacentNodes(start)) {
				if (detectives.stream().anyMatch(e -> destination == e.location())) {
					continue;
				}
				for (ScotlandYard.Transport transport : setup.graph.edgeValueOrDefault(start, destination, ImmutableSet.of())) {
					if (player.has(transport.requiredTicket())) {
						singleMoves.add(new Move.SingleMove(player.piece(), start, transport.requiredTicket(), destination));
					}
					if (player.has(ScotlandYard.Ticket.SECRET)) {
						singleMoves.add(new Move.SingleMove(player.piece(), start, ScotlandYard.Ticket.SECRET, destination));
					}
				}
			}
			return ImmutableSet.copyOf(singleMoves);
		}

		private ImmutableSet<Move.DoubleMove> doubleMoves(GameSetup setup, List<Player> detectives, Player mrX)

		{
			int start = mrX.location();
			List<Move.DoubleMove> doubleMoves = new ArrayList<>();
			if (!mrX.has(ScotlandYard.Ticket.DOUBLE)) {
				//no double ticket, return empty
				return ImmutableSet.copyOf(doubleMoves);
			}
			for (int firstNode : setup.graph.adjacentNodes(start)) {
				if (detectives.stream().anyMatch(e -> firstNode == e.location())) {
					continue;
				}
				for (ScotlandYard.Transport transport1 : setup.graph.edgeValueOrDefault(start, firstNode, ImmutableSet.of())) {
					ScotlandYard.Ticket t1 = transport1.requiredTicket();
					for (int secondNode : setup.graph.adjacentNodes(firstNode)) {
						if (detectives.stream().anyMatch(e -> secondNode == e.location()) || setup.moves.size() < 2) {
							continue;
						}
						for (ScotlandYard.Transport transport2 : setup.graph.edgeValueOrDefault(firstNode, secondNode, ImmutableSet.of())) {
							ScotlandYard.Ticket t2 = transport2.requiredTicket();
							if (mrX.hasAtLeast(t1, 1) && mrX.hasAtLeast(t2, 1) && !Objects.equals(t1, t2)) {
								doubleMoves.add(new Move.DoubleMove(mrX.piece(), start, t1, firstNode, t2, secondNode));
							}
							if (mrX.hasAtLeast(t1, 2) && Objects.equals(t1, t2)) {
								doubleMoves.add(new Move.DoubleMove(mrX.piece(), start, t1, firstNode, t2, secondNode));
							}
							if(mrX.has(ScotlandYard.Ticket.SECRET)){
								doubleMoves.add(new Move.DoubleMove(mrX.piece(), start, t1, firstNode, ScotlandYard.Ticket.SECRET, secondNode));
								doubleMoves.add(new Move.DoubleMove(mrX.piece(), start, ScotlandYard.Ticket.SECRET, firstNode, t2, secondNode));
							}
							if (mrX.hasAtLeast(ScotlandYard.Ticket.SECRET, 2)) {
								doubleMoves.add(new Move.DoubleMove(mrX.piece(), start, ScotlandYard.Ticket.SECRET, firstNode, ScotlandYard.Ticket.SECRET, secondNode));
							}
						}
					}
				}
			}
			return ImmutableSet.copyOf(doubleMoves);
		}
	}




}