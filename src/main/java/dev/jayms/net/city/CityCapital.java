package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Private investor accounts, company ownership and persistent exchange state. */
public final class CityCapital {
    public record Investor(int id, String name, long cents) {}

    public record State(
            List<Investor> investors, List<Integer> graduates, CityStockExchange.State book) {
        public State {
            investors = List.copyOf(investors);
            graduates = List.copyOf(graduates);
            Objects.requireNonNull(book);
        }

        public static State empty() {
            return new State(List.of(), List.of(), CityStockExchange.State.empty());
        }
    }

    private final CityEconomy economy;
    private final Map<Integer, Investor> investors = new LinkedHashMap<>();
    public final Set<Integer> graduates = new LinkedHashSet<>();
    public final CityStockExchange exchange;

    public CityCapital(CityEconomy economy, State state) {
        this.economy = economy;
        for (var p : state.investors()) {
            if (p.id() < 1
                    || p.name().length() > 80
                    || p.cents() < 0
                    || p.cents() > 100_000_000_000_000L
                    || investors.put(p.id(), p) != null)
                throw new IllegalArgumentException("Invalid investor");
        }
        graduates.addAll(state.graduates());
        if (graduates.size() != state.graduates().size()
                || graduates.stream().anyMatch(id -> id < 1))
            throw new IllegalArgumentException("Invalid graduate roster");
        exchange =
                new CityStockExchange(
                        new CityStockExchange.Accounts() {
                            public boolean exists(CityStockExchange.Owner owner) {
                                return owner.kind() == CityEconomy.COMPANY
                                        ? economy.company(owner.id()) != null
                                        : investors.containsKey(owner.id());
                            }

                            public boolean debit(CityStockExchange.Owner owner, long cents) {
                                if (cents < 0 || balance(owner) < cents) return false;
                                move(owner, -cents);
                                return true;
                            }

                            public void credit(CityStockExchange.Owner owner, long cents) {
                                move(owner, cents);
                            }
                        },
                        state.book());
        ensureCompanies();
    }

    public void ensureCompanies() {
        for (var company : economy.companies())
            if (exchange.listing(company.id) == null) {
                // Independent private founders are not city households or self-owned corporations.
                int person =
                        investors.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
                investors.put(person, new Investor(person, company.name + " founder", 150_000));
                exchange.register(
                        company.id, new CityStockExchange.Owner(CityEconomy.CITIZEN, person), 1000);
            }
    }

    public long balance(CityStockExchange.Owner owner) {
        if (owner.kind() == CityEconomy.CITIZEN) {
            var p = investors.get(owner.id());
            return p == null ? -1 : p.cents();
        }
        var firm = economy.company(owner.id());
        return firm == null ? -1 : (long) Math.floor((firm.cash + 1e-9) * 100);
    }

    private void move(CityStockExchange.Owner owner, long cents) {
        if (owner.kind() == CityEconomy.COMPANY) economy.company(owner.id()).cash += cents / 100.0;
        else {
            var p = investors.get(owner.id());
            investors.put(p.id(), new Investor(p.id(), p.name(), Math.addExact(p.cents(), cents)));
        }
    }

    public State state() {
        return new State(
                new ArrayList<>(investors.values()), new ArrayList<>(graduates), exchange.state());
    }

    public static void validate(
            State state, List<CityEconomy.Firm> firms, List<CityFrame.Citizen> citizens) {
        var people = new HashSet<Integer>();
        for (var p : state.investors())
            if (p.id() < 1
                    || p.name().length() > 80
                    || p.cents() < 0
                    || p.cents() > 100_000_000_000_000L
                    || !people.add(p.id())) throw new IllegalArgumentException("Invalid investor");
        var grads = new HashSet<Integer>();
        for (int id : state.graduates())
            if (!grads.add(id) || citizens.stream().noneMatch(c -> c.id() == id))
                throw new IllegalArgumentException("Unknown or duplicate graduate");
        new CityStockExchange(
                new CityStockExchange.Accounts() {
                    public boolean exists(CityStockExchange.Owner owner) {
                        return owner.kind() == 0
                                ? firms.stream().anyMatch(f -> f.id() == owner.id())
                                : people.contains(owner.id());
                    }

                    public boolean debit(CityStockExchange.Owner owner, long cents) {
                        throw new UnsupportedOperationException();
                    }

                    public void credit(CityStockExchange.Owner owner, long cents) {
                        throw new UnsupportedOperationException();
                    }
                },
                state.book());
    }

    private static void owner(DataOutput out, CityStockExchange.Owner owner) throws IOException {
        out.writeByte(owner.kind());
        out.writeInt(owner.id());
    }

    private static CityStockExchange.Owner owner(DataInput in) throws IOException {
        return new CityStockExchange.Owner(in.readUnsignedByte(), in.readInt());
    }

    private static int count(DataInput in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Invalid capital count");
        return n;
    }

    public static void write(DataOutput out, State s) throws IOException {
        out.writeInt(s.investors().size());
        for (var p : s.investors()) {
            out.writeInt(p.id());
            out.writeUTF(p.name());
            out.writeLong(p.cents());
        }
        out.writeInt(s.graduates().size());
        for (int id : s.graduates()) out.writeInt(id);
        var b = s.book();
        out.writeLong(b.nextId());
        out.writeInt(b.listings().size());
        for (var l : b.listings()) {
            out.writeInt(l.company());
            owner(out, l.founder());
            out.writeBoolean(l.publicCompany());
            out.writeLong(l.issued());
            out.writeLong(l.lastPrice());
        }
        out.writeInt(b.holdings().size());
        for (var h : b.holdings()) {
            out.writeInt(h.company());
            owner(out, h.owner());
            out.writeLong(h.shares());
        }
        out.writeInt(b.orders().size());
        for (var o : b.orders()) {
            out.writeLong(o.id());
            out.writeInt(o.company());
            owner(out, o.owner());
            out.writeBoolean(o.buy());
            out.writeBoolean(o.primary());
            out.writeLong(o.shares());
            out.writeLong(o.price());
        }
        out.writeInt(b.trades().size());
        for (var t : b.trades()) {
            out.writeLong(t.sequence());
            out.writeInt(t.company());
            owner(out, t.buyer());
            owner(out, t.seller());
            out.writeLong(t.shares());
            out.writeLong(t.price());
            out.writeBoolean(t.primary());
        }
    }

    public static State read(DataInput in) throws IOException {
        try {
            var people = new ArrayList<Investor>();
            for (int i = 0, n = count(in, 128); i < n; i++)
                people.add(new Investor(in.readInt(), in.readUTF(), in.readLong()));
            var grads = new ArrayList<Integer>();
            for (int i = 0, n = count(in, 128); i < n; i++) grads.add(in.readInt());
            long next = in.readLong();
            var listings = new ArrayList<CityStockExchange.Listing>();
            for (int i = 0, n = count(in, 64); i < n; i++)
                listings.add(
                        new CityStockExchange.Listing(
                                in.readInt(),
                                owner(in),
                                in.readBoolean(),
                                in.readLong(),
                                in.readLong()));
            var holdings = new ArrayList<CityStockExchange.Holding>();
            for (int i = 0, n = count(in, 65536); i < n; i++)
                holdings.add(new CityStockExchange.Holding(in.readInt(), owner(in), in.readLong()));
            var orders = new ArrayList<CityStockExchange.Order>();
            for (int i = 0, n = count(in, 4096); i < n; i++)
                orders.add(
                        new CityStockExchange.Order(
                                in.readLong(),
                                in.readInt(),
                                owner(in),
                                in.readBoolean(),
                                in.readBoolean(),
                                in.readLong(),
                                in.readLong()));
            var trades = new ArrayList<CityStockExchange.Trade>();
            for (int i = 0, n = count(in, 256); i < n; i++)
                trades.add(
                        new CityStockExchange.Trade(
                                in.readLong(),
                                in.readInt(),
                                owner(in),
                                owner(in),
                                in.readLong(),
                                in.readLong(),
                                in.readBoolean()));
            return new State(
                    people,
                    grads,
                    new CityStockExchange.State(next, listings, holdings, orders, trades));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid capital snapshot", e);
        }
    }
}
