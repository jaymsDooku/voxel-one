package dev.jayms.net.city;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class CityStockExchangeTest {
    final CityStockExchange.Owner issuer = new CityStockExchange.Owner(CityEconomy.COMPANY, 1);
    final CityStockExchange.Owner founder = new CityStockExchange.Owner(CityEconomy.CITIZEN, 2);
    final CityStockExchange.Owner buyer = new CityStockExchange.Owner(CityEconomy.CITIZEN, 3);
    final CityStockExchange.Owner firm = new CityStockExchange.Owner(CityEconomy.COMPANY, 4);
    final Map<CityStockExchange.Owner, Long> balances =
            new LinkedHashMap<>(Map.of(issuer, 0L, founder, 10000L, buyer, 10000L, firm, 10000L));
    final CityStockExchange.Accounts accounts =
            new CityStockExchange.Accounts() {
                public boolean exists(CityStockExchange.Owner o) {
                    return balances.containsKey(o);
                }

                public boolean settle(
                        Map<CityStockExchange.Owner, Long> changes,
                        Map<CityStockExchange.Owner, Long> requiredFunds) {
                    for (var required : requiredFunds.entrySet())
                        if (balances.getOrDefault(required.getKey(), -1L) < required.getValue())
                            return false;
                    var updated = new LinkedHashMap<>(balances);
                    for (var change : changes.entrySet()) {
                        long next = Math.addExact(updated.get(change.getKey()), change.getValue());
                        if (next < 0) return false;
                        updated.put(change.getKey(), next);
                    }
                    balances.clear();
                    balances.putAll(updated);
                    return true;
                }
            };

    CityStockExchange exchange() {
        var e = new CityStockExchange(accounts);
        e.register(1, founder, 100);
        return e;
    }

    CityStockExchange listed() {
        var e = exchange();
        e.operational(true);
        e.goPublic(1, founder, 20, 100);
        return e;
    }

    long cash(CityStockExchange e) {
        return balances.values().stream().mapToLong(Long::longValue).sum()
                + e.state().orders().stream()
                        .filter(CityStockExchange.Order::buy)
                        .mapToLong(o -> o.price() * o.shares())
                        .sum();
    }

    @Test
    void startsPrivateAndRequiresOwnerAndExchangeForDilution() {
        var e = exchange();
        assertFalse(e.listing(1).publicCompany());
        assertEquals(100, e.shares(1, founder));
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> e.goPublic(1, founder, 20, 100));
        e.operational(true);
        assertThrows(IllegalArgumentException.class, () -> e.goPublic(1, buyer, 20, 100));
        assertThrows(IllegalArgumentException.class, () -> e.submit(1, buyer, true, 1, 100));
        assertEquals(before, e.state());
        e.goPublic(1, founder, 20, 100);
        assertEquals(120, e.listing(1).issued());
        assertEquals(100, e.shares(1, founder));
        assertEquals(0, balances.get(issuer));
        assertThrows(IllegalArgumentException.class, () -> e.goPublic(1, founder, 20, 100));
    }

    @Test
    void ipoRaisesCompanyCapitalAndRefundsPriceImprovement() {
        var e = listed();
        long total = cash(e);
        e.submit(1, buyer, true, 5, 130);
        assertEquals(500, balances.get(issuer));
        assertEquals(9500, balances.get(buyer));
        assertEquals(10000, balances.get(founder));
        assertEquals(5, e.shares(1, buyer));
        assertEquals(15, e.shares(1, issuer));
        assertEquals(100, e.listing(1).lastPrice());
        assertTrue(e.trades().get(0).primary());
        assertEquals(total, cash(e));
    }

    @Test
    void secondaryTradesPaySellerAndMovePriceThroughSupplyAndDemand() {
        var e = listed();
        e.submit(1, buyer, true, 20, 100);
        long companyCash = balances.get(issuer), total = cash(e);
        e.submit(1, buyer, false, 5, 140);
        assertEquals(100, e.listing(1).lastPrice());
        e.submit(1, firm, true, 3, 150);
        assertEquals(140, e.listing(1).lastPrice());
        assertEquals(companyCash, balances.get(issuer));
        assertEquals(8420, balances.get(buyer));
        assertEquals(3, e.shares(1, firm));
        assertFalse(e.trades().get(1).primary());
        assertEquals(total, cash(e));
    }

    @Test
    void bestPriceThenFifoWithPartialFillsAndMakerPrice() {
        var e = listed();
        e.submit(1, buyer, true, 20, 100);
        e.submit(1, buyer, false, 2, 120);
        e.submit(1, founder, false, 2, 110);
        e.submit(1, founder, false, 2, 120);
        e.submit(1, firm, true, 5, 125);
        var trades = e.trades();
        assertEquals(
                List.of(110L, 120L, 120L),
                trades.subList(1, 4).stream().map(CityStockExchange.Trade::price).toList());
        assertEquals(buyer, trades.get(2).seller());
        assertEquals(1, e.orders(1).get(0).shares());
        var bid = e.submit(1, firm, true, 1, 90);
        e.submit(1, founder, false, 1, 80);
        assertEquals(90, e.listing(1).lastPrice());
        assertFalse(e.cancel(bid, firm));
    }

    @Test
    void reservesCashAndSharesAndCancellationRequiresOrderOwner() {
        var e = listed();
        e.submit(1, buyer, true, 20, 100);
        long ask = e.submit(1, buyer, false, 15, 200);
        assertEquals(5, e.availableShares(1, buyer));
        assertThrows(IllegalArgumentException.class, () -> e.submit(1, buyer, false, 6, 200));
        long bid = e.submit(1, firm, true, 90, 90);
        assertEquals(1900, balances.get(firm));
        assertThrows(IllegalArgumentException.class, () -> e.submit(1, firm, true, 22, 90));
        assertFalse(e.cancel(bid, buyer));
        assertTrue(e.cancel(bid, firm));
        assertEquals(10000, balances.get(firm));
        assertTrue(e.cancel(ask, buyer));
        assertEquals(20, e.availableShares(1, buyer));
    }

    @Test
    void selfOrdersDoNotTradeAndClosurePreservesBookButAllowsCancellation() {
        var e = listed();
        e.submit(1, buyer, true, 20, 100);
        e.submit(1, buyer, false, 5, 200);
        long id = e.submit(1, buyer, true, 5, 200);
        assertEquals(1, e.trades().size());
        var state = e.state();
        e.operational(false);
        assertThrows(IllegalArgumentException.class, () -> e.submit(1, firm, true, 5, 200));
        assertEquals(state, e.state());
        assertTrue(e.cancel(id, buyer));
        assertEquals(8000, balances.get(buyer));
    }

    @Test
    void restoredStateRetainsEscrowAndCanContinueSettlement() {
        var e = listed();
        e.submit(1, buyer, true, 30, 100);
        var saved = e.state();
        var cash = new LinkedHashMap<>(balances);
        var restored = new CityStockExchange(accounts, saved);
        assertEquals(saved, restored.state());
        assertEquals(cash, balances);
        assertFalse(restored.operational());
        restored.operational(true);
        restored.submit(1, founder, false, 5, 90);
        assertEquals(25, restored.shares(1, buyer));
        assertEquals(10500, balances.get(founder));
        assertEquals(30000, cash(restored));
    }

    @Test
    void partialBidFillRefundsOnlyExecutedSharesAndCancellationReturnsRemainder() {
        var e = listed();
        e.submit(1, buyer, true, 20, 100);
        long total = cash(e);
        long bid = e.submit(1, firm, true, 10, 150);
        e.submit(1, founder, false, 4, 120);
        assertEquals(150, e.listing(1).lastPrice());
        assertEquals(8500, balances.get(firm));
        assertEquals(6, e.orders(1).get(0).shares());
        assertEquals(total, cash(e));
        assertTrue(e.cancel(bid, firm));
        assertEquals(9400, balances.get(firm));
        assertEquals(total, cash(e));
        assertEquals(4, e.shares(1, firm));
    }

    @Test
    void rejectsDuplicateReferencesAndUnknownShareholdersOnRestore() {
        var e = listed();
        var saved = e.state();
        var order = saved.orders().get(0);
        var duplicate =
                new CityStockExchange.State(
                        saved.nextId(),
                        saved.listings(),
                        saved.holdings(),
                        List.of(order, order),
                        saved.trades());
        assertThrows(
                IllegalArgumentException.class, () -> new CityStockExchange(accounts, duplicate));
        balances.remove(founder);
        assertThrows(IllegalArgumentException.class, () -> new CityStockExchange(accounts, saved));
    }

    @Test
    void invalidInputAndCorruptSnapshotsAreRejectedWithoutMutation() {
        var e = listed();
        var saved = e.state();
        assertThrows(IllegalArgumentException.class, () -> e.submit(1, buyer, true, -1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> e.submit(1, buyer, true, 1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> e.submit(99, buyer, true, 1, 1));
        assertEquals(saved, e.state());
        var bad =
                new CityStockExchange.State(
                        saved.nextId(),
                        saved.listings(),
                        List.of(new CityStockExchange.Holding(1, founder, 99)),
                        saved.orders(),
                        saved.trades());
        assertThrows(IllegalArgumentException.class, () -> new CityStockExchange(accounts, bad));
        var oversold =
                new CityStockExchange.State(
                        saved.nextId(),
                        saved.listings(),
                        saved.holdings(),
                        List.of(new CityStockExchange.Order(1, 1, founder, false, false, 101, 100)),
                        List.of());
        assertThrows(
                IllegalArgumentException.class, () -> new CityStockExchange(accounts, oversold));
    }
}
