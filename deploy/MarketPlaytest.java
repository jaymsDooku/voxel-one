package dev.jayms;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.lwjgl.opengl.GL;
import java.nio.file.*;

/** Native dashboard playtest using synthetic market fixtures, never account data. */
public final class MarketPlaytest {
    static CitySimulation fixture(boolean shared) throws Exception {
        var method = MarketEconomyTest.class.getDeclaredMethod("mealStockFixture", boolean.class);
        method.setAccessible(true);
        var fixture = method.invoke(new MarketEconomyTest(), shared);
        var run = fixture.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        return (CitySimulation) run.invoke(fixture);
    }
    static void capture(Overlay overlay, BusinessDashboard dashboard, CitySimulation city,
                        Path path) throws Exception {
        glViewport(0, 0, 1280, 720);
        glClearColor(.04f, .06f, .08f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        overlay.begin(1280, 720);
        dashboard.render(overlay, 1280, 720, city.frame());
        overlay.end();
        SpecialBuildingsSmoke.capture(path);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
    }
    static void exchangeCapture(Overlay overlay, CitySimulation city, Path path) throws Exception {
        var inspector = new BuildingInfo();
        inspector.building = new ExchangeLabourMarketTest().exchange(city);
        inspector.open = true;
        inspector.click(290, 100, 1280, 720);
        glClearColor(.04f, .06f, .08f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        overlay.begin(1280, 720);
        inspector.render(overlay, 1280, 720, city.frame());
        overlay.text(String.format(java.util.Locale.ROOT,
                "Playtest: hourly analyst $%.3f | support $%.3f | treasury $%.3f",
                city.exchangeLabourRate(true), city.exchangeLabourRate(false), city.economy.budget),
                24, 8, 1.2f);
        overlay.text("Playtest: exchange trading "
                + (city.economy.capital.exchange.operational() ? "OPEN" : "CLOSED"),
                24, 704, 1.2f);
        overlay.end();
        SpecialBuildingsSmoke.capture(path);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
    }
    static void restockCapture(Overlay overlay, CitySimulation city, Path path) throws Exception {
        var tests = new ShopRestockMarketTest();
        var inspector = new BuildingInfo();
        inspector.building = ShopRestockMarketTest.SHOP;
        inspector.open = true;
        inspector.click(750, 100, 1280, 720);
        glClearColor(.04f, .06f, .08f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        overlay.begin(1280, 720);
        inspector.render(overlay, 1280, 720, city.frame());
        overlay.text(String.format(java.util.Locale.ROOT,
                "Playtest: shop Meal A=%d | Meal B=%d | cash $%.3f",
                tests.stock(city, ShopRestockMarketTest.A), tests.stock(city, ShopRestockMarketTest.B),
                tests.shop(city).cash), 24, 8, 1.2f);
        overlay.end();
        SpecialBuildingsSmoke.capture(path);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
    }
    public static void main(String[] args) throws Exception {
        boolean offscreen = args.length > 1 && args[1].equals("offscreen");
        long window = 0;
        if (!offscreen) {
        if (!glfwInit()) throw new AssertionError("GLFW unavailable");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(1280, 720, "Market workflow playtest", 0, 0);
        if (window == 0) throw new AssertionError("Window unavailable");
        glfwMakeContextCurrent(window);
        }
        GL.createCapabilities();
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        try (var overlay = new Overlay()) {
            var dashboard = new BusinessDashboard();
            var city = fixture(true);
            dashboard.click(550, 190, 1280, 720);
            capture(overlay, dashboard, city, out.resolve("market-current-businesses.png"));
            int index = java.util.stream.IntStream.range(0, dashboard.rows(city.frame()).size())
                    .filter(i -> dashboard.rows(city.frame()).get(i).building().id() == 601)
                    .findFirst().orElseThrow();
            dashboard.click(100, 466 + index * 32 + 5, 1280, 720);
            if (dashboard.selected != 601) throw new AssertionError("Shop row click failed");
            capture(overlay, dashboard, city, out.resolve("market-current-before.png"));
            city.advance(.11);
            var sale = city.economy.businesses.records().stream()
                    .filter(r -> r.building() == 601).findFirst().orElseThrow();
            if (sale.total().sold() != 4) throw new AssertionError("Expected four portions sold");
            if (city.frame().buildings().stream().mapToInt(CityFrame.Building::stock).sum() != 16)
                throw new AssertionError("Expected sixteen portions remaining");
            capture(overlay, dashboard, city, out.resolve("market-current-purchase.png"));
            var edge = fixture(false);
            edge.advance(.11);
            if (edge.economy.businesses.records().stream().anyMatch(r -> r.total().sold() != 0))
                throw new AssertionError("Low stock sold an unavailable meal");
            dashboard.click(100, 190, 1280, 720);
            capture(overlay, dashboard, edge, out.resolve("market-current-edge-businesses.png"));
            dashboard.click(100, 471, 1280, 720);
            if (dashboard.selected != 600) throw new AssertionError("Low-stock row click failed");
            capture(overlay, dashboard, edge, out.resolve("market-current-low-stock.png"));
            var tests = new MarketEconomyTest();
            tests.householdsChooseCheapestMealMeetingNutritionAndPayTheActualSeller();
            tests.buyersChooseCheapestSupplierAndPartialAffordableFillsConserveAssets();
            tests.lowStockShopCannotSellMultiPortionMealBeforeRestocking();
            tests.sharedCompanyShopsUseLocalStockAndReconcileSales();
            var exchangeTests = new ExchangeLabourMarketTest();
            var office = exchangeTests.fixture();
            new CityCapitalTest().staff(office);
            for (var firm : office.economy.companies()) firm.cash = 0;
            office.advance(.11);
            if (!office.economy.capital.exchange.operational())
                throw new AssertionError("Funded qualified exchange not operational");
            exchangeCapture(overlay, office, out.resolve("market-exchange-paid.png"));
            office.economy.budget = 0;
            office.advance(.11);
            if (office.economy.capital.exchange.operational())
                throw new AssertionError("Unfunded exchange remained operational");
            exchangeCapture(overlay, office, out.resolve("market-exchange-unfunded.png"));
            exchangeTests.quotesRespondToQualifiedSupplyAndExchangeDemand();
            exchangeTests.actualPayrollUsesMarketQuotesAndOnlyTreasuryFunds();
            exchangeTests.unfundedExchangeDoesNotHireAndWorkersCanTakeFundedPrivateJobs();
            exchangeTests.hiringKeepsSkillSlotsAndPaidWorkersCanChooseBetterOffers();
            exchangeTests.unqualifiedApplicantsCannotFillAnalystVacancies();
            exchangeTests.graduatingSupportWorkerDoesNotOverfillOffice();
            var restockTests = new ShopRestockMarketTest();
            var restocked = restockTests.fixture(1, 1.02, 10, 30);
            restocked.economy.resources.add(0, restockTests.sellers(restocked).get(2).id,
                    ShopRestockMarketTest.A, 10 * CityMaterials.UNIT);
            restocked.economy.resources.add(0, restockTests.sellers(restocked).get(3).id,
                    ShopRestockMarketTest.A, 10 * CityMaterials.UNIT);
            restocked.advance(.11);
            if (restockTests.stock(restocked, ShopRestockMarketTest.A) != 0
                    || restockTests.stock(restocked, ShopRestockMarketTest.B) != 16)
                throw new AssertionError("Restock chose dearer supplier offers");
            restockCapture(overlay, restocked, out.resolve("market-restock-cheapest-offer.png"));
            var limited = restockTests.fixture(1, 1.02, 0, 30);
            restockTests.shop(limited).cash = .5;
            limited.advance(.11);
            if (restockTests.stock(limited, ShopRestockMarketTest.B) != 2
                    || restockTests.shop(limited).cash < 0)
                throw new AssertionError("Restock affordability failed");
            restockCapture(overlay, limited, out.resolve("market-restock-budget-edge.png"));
            restockTests.reversedReferenceAndSupplierOfferOrderingBuysCheapestSuitableFood();
            restockTests.supplierDepletionRechecksSubstitutesBeforeMoreExpensiveFills();
            restockTests.unaffordableAndEmptyOffersDoNotSpendMoneyOrOverdraw();
            restockTests.higherNutritionCanBeatLowerPortionPrice();
            System.out.println("PASS: native dashboard; four-portion purchase; low-stock rejection; cheapest suitable meal; cheapest supplier; partial fill; conservation; shared-company stock");
            System.out.println("PASS: exchange market wages; qualified staffing; treasury payroll; unfunded closure; funded private job choice");
            System.out.println("PASS: restock actual cheapest offers; reversed reference order; changing supplier quotes; affordable portions; nutrition value; eligible suppliers");
        } finally {
            if (!offscreen) { glfwDestroyWindow(window); glfwTerminate(); }
        }
    }
}
