package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.city.*;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

class CapitalSettlementLimitsTest {
    CityEconomy economy(long founderCash) {
        var original = new CityEconomy(new Ecs(), null).state();
        var investors = new ArrayList<>(original.capital().investors());
        var p = investors.get(0);
        investors.set(0, new CityCapital.Investor(p.id(), p.name(), founderCash));
        var capital = new CityCapital.State(investors, List.of(), original.capital().book());
        return new CityEconomy(
                new Ecs(),
                new CityEconomy.State(
                        original.budget(),
                        original.roadSpending(),
                        original.landRevenue(),
                        original.rentClock(),
                        original.firms(),
                        original.plots(),
                        original.properties(),
                        original.contracts(),
                        original.businesses(),
                        original.resources(),
                        capital));
    }

    CityStockExchange.Owner founder(CityEconomy e) {
        return e.capital.exchange.listing(e.companies().get(0).id).founder();
    }

    CityStockExchange.Owner buyer(CityEconomy e) {
        return e.capital.exchange.listing(e.companies().get(1).id).founder();
    }

    int listed(CityEconomy e) {
        int id = e.companies().get(0).id;
        e.capital.exchange.operational(true);
        e.capital.exchange.goPublic(id, founder(e), 1, 1000);
        return id;
    }

    void roundTrip(CityEconomy e) throws Exception {
        var frame =
                new CityFrame(
                        GameConfig.cityGame(),
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        e.state());
        CityCapital.validate(e.capital.state(), e.state().firms(), List.of());
        var bytes = new ByteArrayOutputStream();
        frame.write(new DataOutputStream(bytes));
        assertEquals(
                frame,
                CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void investorAtMaximumRejectsSellerProceedsWithoutChangingBuyerOrBook() throws Exception {
        var e = economy(CityCapital.MAX_INVESTOR_CENTS);
        int id = listed(e);
        var book = e.capital.exchange;
        book.submit(id, founder(e), false, 1, 100);
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.submit(id, buyer(e), true, 1, 150));
        assertEquals(before, e.state());
        roundTrip(e);
    }

    @Test
    void rejectedSellerOrderLeavesRestingBuyerEscrowUntouched() throws Exception {
        var e = economy(CityCapital.MAX_INVESTOR_CENTS);
        int id = listed(e);
        long bid = e.capital.exchange.submit(id, buyer(e), true, 1, 100);
        var before = e.state();
        assertThrows(
                IllegalArgumentException.class,
                () -> e.capital.exchange.submit(id, founder(e), false, 1, 100));
        assertEquals(before, e.state());
        roundTrip(e);
        assertTrue(e.capital.exchange.cancel(bid, buyer(e)));
        assertEquals(150000, e.capital.balance(buyer(e)));
        roundTrip(e);
    }

    @Test
    void investorCanReachExactMaximumAndNextSaleIsAtomicAcrossMultipleFills() throws Exception {
        var e = economy(CityCapital.MAX_INVESTOR_CENTS - 100);
        int id = listed(e);
        var book = e.capital.exchange;
        book.submit(id, founder(e), false, 1, 100);
        book.submit(id, founder(e), false, 1, 100);
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.submit(id, buyer(e), true, 2, 100));
        assertEquals(before, e.state());
        book.submit(id, buyer(e), true, 1, 150);
        assertEquals(CityCapital.MAX_INVESTOR_CENTS, e.capital.balance(founder(e)));
        assertEquals(149900, e.capital.balance(buyer(e)));
        roundTrip(e);
        var capped = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.submit(id, buyer(e), true, 1, 100));
        assertEquals(capped, e.state());
        roundTrip(e);
    }

    @Test
    void companyIpoProceedsRespectSnapshotMaximum() throws Exception {
        var e = economy(150000);
        int id = listed(e);
        var book = e.capital.exchange;
        e.company(id).cash = CityEconomy.MAX_BALANCE;
        var before = e.state();
        assertThrows(
                IllegalArgumentException.class, () -> book.submit(id, buyer(e), true, 1, 1000));
        assertEquals(before, e.state());
        roundTrip(e);
        e.company(id).cash = CityEconomy.MAX_BALANCE - 10;
        book.submit(id, buyer(e), true, 1, 1000);
        assertEquals(CityEconomy.MAX_BALANCE, e.company(id).cash);
        roundTrip(e);
    }

    @Test
    void cancellationRefundThatWouldOverflowKeepsEscrowAndCanBeRetried() throws Exception {
        var e = economy(150000);
        int id = listed(e);
        var book = e.capital.exchange;
        var company = new CityStockExchange.Owner(0, e.companies().get(1).id);
        long bid = book.submit(id, company, true, 1, 100);
        e.company(company.id()).cash = CityEconomy.MAX_BALANCE;
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.cancel(bid, company));
        assertEquals(before, e.state());
        roundTrip(e);
        e.company(company.id()).cash = CityEconomy.MAX_BALANCE - 1;
        assertTrue(book.cancel(bid, company));
        assertEquals(CityEconomy.MAX_BALANCE, e.company(company.id()).cash);
        roundTrip(e);
    }

    @Test
    void buyerRefundAndSellerProceedsMustBothFitBeforeRestingBidExecutes() throws Exception {
        var e = economy(150000);
        int id = listed(e);
        var book = e.capital.exchange;
        // A resting bid executes at its own price; remaining buy escrow remains reserved.
        var company = new CityStockExchange.Owner(0, e.companies().get(1).id);
        long bid = book.submit(id, company, true, 2, 100);
        e.company(company.id()).cash = CityEconomy.MAX_BALANCE;
        book.submit(id, founder(e), false, 1, 50);
        assertEquals(100, book.listing(id).lastPrice());
        assertEquals(CityEconomy.MAX_BALANCE, e.company(company.id()).cash);
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.cancel(bid, company));
        assertEquals(before, e.state());
        roundTrip(e);
    }

    @Test
    void investorCancellationRefundAtLimitIsAtomicAndSaveable() throws Exception {
        var original = economy(150000);
        int id = listed(original);
        var owner = buyer(original);
        long bid = original.capital.exchange.submit(id, owner, true, 1, 100);
        var state = original.state();
        var people = new ArrayList<>(state.capital().investors());
        for (int i = 0; i < people.size(); i++)
            if (people.get(i).id() == owner.id()) {
                var p = people.get(i);
                people.set(
                        i,
                        new CityCapital.Investor(p.id(), p.name(), CityCapital.MAX_INVESTOR_CENTS));
            }
        var e =
                new CityEconomy(
                        new Ecs(),
                        new CityEconomy.State(
                                state.budget(),
                                state.roadSpending(),
                                state.landRevenue(),
                                state.rentClock(),
                                state.firms(),
                                state.plots(),
                                state.properties(),
                                state.contracts(),
                                state.businesses(),
                                state.resources(),
                                new CityCapital.State(people, List.of(), state.capital().book())));
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> e.capital.exchange.cancel(bid, owner));
        assertEquals(before, e.state());
        roundTrip(e);
        e.capital.exchange.operational(true);
        long spending = e.capital.exchange.submit(id, owner, true, 1, 100);
        assertTrue(e.capital.exchange.cancel(bid, owner));
        assertEquals(CityCapital.MAX_INVESTOR_CENTS, e.capital.balance(owner));
        assertTrue(e.capital.exchange.orders(id).stream().anyMatch(o -> o.id() == spending));
        roundTrip(e);
    }

    @Test
    void fractionalCompanyHeadroomCannotBeRoundedIntoCapacity() throws Exception {
        var e = economy(150000);
        int id = listed(e);
        e.company(id).cash = CityEconomy.MAX_BALANCE - 9.995;
        var before = e.state();
        assertThrows(
                IllegalArgumentException.class,
                () -> e.capital.exchange.submit(id, buyer(e), true, 1, 1000));
        assertEquals(before, e.state());
        roundTrip(e);
    }

    @Test
    void buyMustAffordFullLimitEvenWhenExecutionWouldBeCheaper() throws Exception {
        var e = economy(150000);
        int id = listed(e);
        var book = e.capital.exchange;
        book.submit(id, founder(e), false, 1, 100);
        var company = new CityStockExchange.Owner(0, e.companies().get(1).id);
        e.company(company.id()).cash = 1;
        var before = e.state();
        assertThrows(IllegalArgumentException.class, () -> book.submit(id, company, true, 1, 150));
        assertEquals(before, e.state());
        roundTrip(e);
    }
}
