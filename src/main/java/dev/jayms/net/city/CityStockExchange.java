package dev.jayms.net.city;

import java.util.*;

/** Share ledger and price/time ordered limit book. All cash prices are integer cents. */
public final class CityStockExchange {
    public record Owner(int kind, int id) {
        public Owner {
            if ((kind != CityEconomy.COMPANY && kind != CityEconomy.CITIZEN) || id < 1)
                throw new IllegalArgumentException("Invalid shareholder");
        }
    }

    /** Accounts must debit atomically and credit exactly; no conversion to floating point here. */
    public interface Accounts {
        boolean exists(Owner owner);

        boolean debit(Owner owner, long cents);

        void credit(Owner owner, long cents);
    }

    public record Listing(
            int company, Owner founder, boolean publicCompany, long issued, long lastPrice) {}

    public record Holding(int company, Owner owner, long shares) {}

    public record Order(
            long id,
            int company,
            Owner owner,
            boolean buy,
            boolean primary,
            long shares,
            long price) {}

    public record Trade(
            long sequence,
            int company,
            Owner buyer,
            Owner seller,
            long shares,
            long price,
            boolean primary) {}

    public record State(
            long nextId,
            List<Listing> listings,
            List<Holding> holdings,
            List<Order> orders,
            List<Trade> trades) {
        public State {
            listings = List.copyOf(listings);
            holdings = List.copyOf(holdings);
            orders = List.copyOf(orders);
            trades = List.copyOf(trades);
        }

        public static State empty() {
            return new State(0, List.of(), List.of(), List.of(), List.of());
        }
    }

    public static final long MAX_SHARES = 1_000_000_000L, MAX_PRICE = 1_000_000_000L;
    private final Accounts accounts;
    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final Map<Integer, Map<Owner, Long>> holdings = new LinkedHashMap<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Trade> trades = new ArrayList<>();
    private long nextId;
    private boolean operational;

    public CityStockExchange(Accounts accounts) {
        this(accounts, State.empty());
    }

    /** Restoring a book does not debit escrow again. Escrow already left the saved accounts. */
    public CityStockExchange(Accounts accounts, State state) {
        this.accounts = Objects.requireNonNull(accounts);
        validate(state);
        nextId = state.nextId();
        for (var listing : state.listings()) {
            listings.put(listing.company(), listing);
            holdings.put(listing.company(), new LinkedHashMap<>());
        }
        for (var holding : state.holdings())
            holdings.get(holding.company()).put(holding.owner(), holding.shares());
        orders.addAll(state.orders());
        trades.addAll(state.trades());
    }

    /**
     * The building/employment system supplies availability; closure preserves escrow and the book.
     */
    public void operational(boolean value) {
        operational = value;
    }

    public boolean operational() {
        return operational;
    }

    public Listing listing(int company) {
        return listings.get(company);
    }

    public List<Order> orders(int company) {
        return orders.stream()
                .filter(o -> o.company() == company)
                .sorted(
                        Comparator.comparing(Order::buy)
                                .reversed()
                                .thenComparingLong(o -> o.buy() ? -o.price() : o.price())
                                .thenComparingLong(Order::id))
                .toList();
    }

    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    public long shares(int company, Owner owner) {
        return holdings.getOrDefault(company, Map.of()).getOrDefault(owner, 0L);
    }

    public long availableShares(int company, Owner owner) {
        return shares(company, owner)
                - orders.stream()
                        .filter(o -> o.company() == company && o.owner().equals(owner) && !o.buy())
                        .mapToLong(Order::shares)
                        .sum();
    }

    public void register(int company, Owner founder, long shares) {
        if (company < 1
                || listings.containsKey(company)
                || !accounts.exists(founder)
                || !accounts.exists(new Owner(CityEconomy.COMPANY, company)))
            throw new IllegalArgumentException("Invalid company / founder");
        quantity(shares);
        listings.put(company, new Listing(company, founder, false, shares, 0));
        holdings.put(company, new LinkedHashMap<>(Map.of(founder, shares)));
    }

    /** Only the private owner can authorize dilution. Newly issued shares raise company capital. */
    public long goPublic(int company, Owner actor, long newShares, long price) {
        var listing = listings.get(company);
        if (!operational
                || listing == null
                || listing.publicCompany()
                || !listing.founder().equals(actor)
                || !accounts.exists(actor))
            throw new IllegalArgumentException("Offering requires owner and an operating exchange");
        quantity(newShares);
        price(price);
        if (listing.issued() > MAX_SHARES - newShares || orders.size() >= 4096)
            throw new IllegalArgumentException("Offering limit");
        var issuer = new Owner(CityEconomy.COMPANY, company);
        listings.put(company, new Listing(company, actor, true, listing.issued() + newShares, 0));
        addShares(company, issuer, newShares);
        long id = ++nextId;
        orders.add(new Order(id, company, issuer, false, true, newShares, price));
        match(company);
        return id;
    }

    /** Cash is escrowed at the buy limit; sells reserve owned shares without allowing shorts. */
    public long submit(int company, Owner owner, boolean buy, long shares, long price) {
        var listing = listings.get(company);
        if (!operational
                || listing == null
                || !listing.publicCompany()
                || !accounts.exists(owner)
                || orders.size() >= 4096)
            throw new IllegalArgumentException(
                    "Trading requires a public company and operating exchange");
        quantity(shares);
        price(price);
        if (buy) {
            if (!accounts.debit(owner, Math.multiplyExact(shares, price)))
                throw new IllegalArgumentException("Insufficient cash");
        } else if (availableShares(company, owner) < shares)
            throw new IllegalArgumentException("Insufficient unreserved shares");
        long id = ++nextId;
        orders.add(new Order(id, company, owner, buy, false, shares, price));
        match(company);
        return id;
    }

    /** Cancellation remains available during closure. A caller can cancel only its own orders. */
    public boolean cancel(long id, Owner actor) {
        var order = orders.stream().filter(o -> o.id() == id).findFirst().orElse(null);
        if (order == null || !order.owner().equals(actor)) return false;
        orders.remove(order);
        if (order.buy()) accounts.credit(actor, Math.multiplyExact(order.shares(), order.price()));
        return true;
    }

    private void match(int company) {
        while (true) {
            Order bid = null, ask = null;
            var bids = orders(company).stream().filter(Order::buy).toList();
            var asks = orders(company).stream().filter(o -> !o.buy()).toList();
            // Skip self matches. Highest eligible bid then lowest eligible ask, with FIFO ties.
            outer:
            for (var b : bids)
                for (var a : asks) {
                    if (a.price() > b.price()) break;
                    if (!a.owner().equals(b.owner())) {
                        bid = b;
                        ask = a;
                        break outer;
                    }
                }
            if (bid == null) return;
            long units = Math.min(bid.shares(), ask.shares());
            long execution = bid.id() < ask.id() ? bid.price() : ask.price();
            accounts.credit(ask.owner(), Math.multiplyExact(units, execution));
            if (execution < bid.price())
                accounts.credit(bid.owner(), Math.multiplyExact(units, bid.price() - execution));
            addShares(company, ask.owner(), -units);
            addShares(company, bid.owner(), units);
            reduce(bid, units);
            reduce(ask, units);
            var listing = listings.get(company);
            listings.put(
                    company,
                    new Listing(company, listing.founder(), true, listing.issued(), execution));
            trades.add(
                    new Trade(
                            ++nextId,
                            company,
                            bid.owner(),
                            ask.owner(),
                            units,
                            execution,
                            ask.primary()));
            if (trades.size() > 256) trades.remove(0);
        }
    }

    private void addShares(int company, Owner owner, long delta) {
        var companyHoldings = holdings.get(company);
        long total = companyHoldings.getOrDefault(owner, 0L) + delta;
        if (total == 0) companyHoldings.remove(owner);
        else companyHoldings.put(owner, total);
    }

    private void reduce(Order order, long units) {
        int index = orders.indexOf(order);
        if (order.shares() == units) orders.remove(index);
        else
            orders.set(
                    index,
                    new Order(
                            order.id(),
                            order.company(),
                            order.owner(),
                            order.buy(),
                            order.primary(),
                            order.shares() - units,
                            order.price()));
    }

    private static void quantity(long value) {
        if (value < 1 || value > MAX_SHARES)
            throw new IllegalArgumentException("Invalid share quantity");
    }

    private static void price(long value) {
        if (value < 1 || value > MAX_PRICE)
            throw new IllegalArgumentException("Invalid limit price");
    }

    public State state() {
        var savedHoldings = new ArrayList<Holding>();
        holdings.forEach(
                (company, owners) ->
                        owners.forEach(
                                (owner, shares) ->
                                        savedHoldings.add(new Holding(company, owner, shares))));
        return new State(nextId, new ArrayList<>(listings.values()), savedHoldings, orders, trades);
    }

    private void validate(State state) {
        if (state.nextId() < 0
                || state.nextId() > Long.MAX_VALUE - 8192
                || state.listings().size() > 64
                || state.holdings().size() > 65536
                || state.orders().size() > 4096
                || state.trades().size() > 256)
            throw new IllegalArgumentException("Invalid exchange state size");
        var companies = new HashMap<Integer, Listing>();
        var held = new HashMap<HoldingKey, Long>();
        var reserved = new HashMap<HoldingKey, Long>();
        var ids = new HashSet<Long>();
        for (var l : state.listings()) {
            quantity(l.issued());
            if (l.company() < 1
                    || !accounts.exists(l.founder())
                    || !accounts.exists(new Owner(CityEconomy.COMPANY, l.company()))
                    || l.lastPrice() < 0
                    || l.lastPrice() > MAX_PRICE
                    || !l.publicCompany() && l.lastPrice() != 0
                    || companies.put(l.company(), l) != null)
                throw new IllegalArgumentException("Invalid listing");
        }
        for (var h : state.holdings()) {
            quantity(h.shares());
            if (!companies.containsKey(h.company())
                    || !accounts.exists(h.owner())
                    || held.put(new HoldingKey(h.company(), h.owner()), h.shares()) != null)
                throw new IllegalArgumentException("Invalid holding");
        }
        for (var l : state.listings()) {
            long sum =
                    state.holdings().stream()
                            .filter(h -> h.company() == l.company())
                            .mapToLong(Holding::shares)
                            .sum();
            if (sum != l.issued()
                    || !l.publicCompany()
                            && held.getOrDefault(new HoldingKey(l.company(), l.founder()), 0L)
                                    != sum)
                throw new IllegalArgumentException("Share conservation failure");
        }
        for (var o : state.orders()) {
            quantity(o.shares());
            price(o.price());
            var l = companies.get(o.company());
            if (l == null
                    || !l.publicCompany()
                    || !accounts.exists(o.owner())
                    || o.id() <= 0
                    || o.id() > state.nextId()
                    || !ids.add(o.id())
                    || o.primary()
                            && (o.buy()
                                    || !o.owner()
                                            .equals(new Owner(CityEconomy.COMPANY, o.company()))))
                throw new IllegalArgumentException("Invalid saved order");
            if (!o.buy())
                reserved.merge(new HoldingKey(o.company(), o.owner()), o.shares(), Long::sum);
        }
        reserved.forEach(
                (key, amount) -> {
                    if (amount > held.getOrDefault(key, 0L))
                        throw new IllegalArgumentException("Oversubscribed shares");
                });
        long previous = 0;
        for (var t : state.trades()) {
            quantity(t.shares());
            price(t.price());
            var l = companies.get(t.company());
            if (l == null
                    || !l.publicCompany()
                    || !accounts.exists(t.buyer())
                    || !accounts.exists(t.seller())
                    || t.buyer().equals(t.seller())
                    || t.sequence() <= previous
                    || t.sequence() > state.nextId()
                    || !ids.add(t.sequence())
                    || t.primary()
                            && !t.seller().equals(new Owner(CityEconomy.COMPANY, t.company())))
                throw new IllegalArgumentException("Invalid saved trade");
            previous = t.sequence();
        }
    }

    private record HoldingKey(int company, Owner owner) {}
}
