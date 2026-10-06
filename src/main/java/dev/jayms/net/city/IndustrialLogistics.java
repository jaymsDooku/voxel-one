package dev.jayms.net.city;

import java.util.*;

/** Private freight fleets use manufactured assets and burn owned energy on distant deliveries.
 * Transport is a market service; products represent the fleet, track and depot investment.
 */
public final class IndustrialLogistics {
    public static final int LOCAL_RANGE = 96;
    public record Mode(String name, int range, double speed, int vehicle, int energy, int depot) {}
    public static final List<Mode> MODES = List.of(
            new Mode("Local hand delivery", LOCAL_RANGE, 2.2, 0, 0, 0),
            new Mode("Cart", 128, 3.5, IndustrialProgression.CART, 0, 0),
            new Mode("Freight train", 512, 8, IndustrialProgression.FREIGHT_TRAIN, IndustrialProgression.COAL, IndustrialProgression.STATION),
            new Mode("Truck", 192, 6, IndustrialProgression.TRUCK, IndustrialProgression.FUEL, 0),
            new Mode("Road freight", 256, 7, IndustrialProgression.CAR, IndustrialProgression.FUEL, 0),
            new Mode("Electric rail", 512, 10, IndustrialProgression.ELECTRIC_RAIL, IndustrialProgression.POWER, IndustrialProgression.STATION),
            new Mode("Modern train", 768, 12, IndustrialProgression.MODERN_TRAIN, IndustrialProgression.POWER, IndustrialProgression.STATION),
            new Mode("Advanced road freight", 384, 9, IndustrialProgression.ADVANCED_VEHICLE, IndustrialProgression.FUEL, 0));

    public static Mode mode(CityMaterials stock, int ownerKind, int owner) {
        Mode best = MODES.get(0);
        if (!IndustrialProgression.enabled(stock.catalog)) return best;
        int tier = IndustrialProgression.tier(stock);
        for (var mode : MODES.subList(1, MODES.size())) {
            int required = mode.vehicle() == IndustrialProgression.CART ? 2
                    : mode.vehicle() == IndustrialProgression.FREIGHT_TRAIN ? 4
                    : mode.vehicle() == IndustrialProgression.TRUCK ? 5
                    : mode.vehicle() == IndustrialProgression.CAR ? 6
                    : mode.vehicle() == IndustrialProgression.ELECTRIC_RAIL ? 7 : 8;
            if (tier < required) continue;
            if (stock.available(ownerKind, owner, mode.vehicle()) < CityMaterials.UNIT
                    || mode.depot() != 0 && stock.available(ownerKind, owner, mode.depot()) < CityMaterials.UNIT
                    || mode.energy() != 0 && stock.available(ownerKind, owner, mode.energy()) < CityMaterials.UNIT)
                continue;
            if (mode.range() > best.range() || mode.range() == best.range() && mode.speed() > best.speed()) best = mode;
        }
        return best;
    }
    public static long energy(Mode mode, double distance, long units) {
        if (mode.energy() == 0 || distance <= LOCAL_RANGE) return 0;
        return Math.max(1, (long) Math.ceil(distance / 64 * units / 16));
    }
    public static boolean deliver(CityMaterials stock, int buyerKind, int buyer, double distance, long units) {
        if (!Double.isFinite(distance) || distance < 0 || units <= 0 || units > 1_000_000_000L
                || buyerKind < 0 || buyerKind > 1 || buyer < 1) return false;
        if (!IndustrialProgression.enabled(stock.catalog)) return true;
        var mode = mode(stock, buyerKind, buyer);
        if (distance > mode.range()) return false;
        long energy = energy(mode, distance, units);
        return energy == 0 || stock.remove(buyerKind, buyer, mode.energy(), energy);
    }
    /** Fleet investment is paid from private company cash, through the existing stock market. */
    public static void provision(CityEconomy economy, int company) {
        var stock = economy.resources;
        if (!IndustrialProgression.enabled(stock.catalog)) return;
        int tier = IndustrialProgression.tier(stock);
        int[] assets = tier >= 8 ? new int[]{IndustrialProgression.MODERN_TRAIN, IndustrialProgression.STATION}
                : tier >= 7 ? new int[]{IndustrialProgression.ELECTRIC_RAIL, IndustrialProgression.STATION}
                : tier >= 6 ? new int[]{IndustrialProgression.CAR}
                : tier >= 5 ? new int[]{IndustrialProgression.TRUCK}
                : tier >= 4 ? new int[]{IndustrialProgression.FREIGHT_TRAIN, IndustrialProgression.STATION}
                : tier >= 2 ? new int[]{IndustrialProgression.CART} : new int[0];
        for (int asset : assets) economy.purchase(company, asset, CityMaterials.UNIT);
        if (tier >= 4) economy.purchase(company, IndustrialProgression.COAL, 4 * CityMaterials.UNIT);
        if (tier >= 5) economy.purchase(company, IndustrialProgression.FUEL, 4 * CityMaterials.UNIT);
        if (tier >= 7) economy.purchase(company, IndustrialProgression.POWER, 4 * CityMaterials.UNIT);
        if (tier >= 3) economy.purchase(company, IndustrialProgression.MACHINERY, CityMaterials.UNIT);
        if (tier >= 7) economy.purchase(company, IndustrialProgression.ELECTRICAL, CityMaterials.UNIT);
    }
    public static double productivity(CityMaterials stock, int company) {
        if (!IndustrialProgression.enabled(stock.catalog)) return 1;
        if (stock.available(0, company, IndustrialProgression.ELECTRICAL) >= CityMaterials.UNIT
                && stock.available(0, company, IndustrialProgression.POWER) >= CityMaterials.UNIT) return 2;
        return stock.available(0, company, IndustrialProgression.MACHINERY) >= CityMaterials.UNIT ? 1.5 : 1;
    }
    public static boolean denseHousing(CityMaterials.State state) {
        return IndustrialProgression.enabled(state.catalog()) && IndustrialProgression.tier(state) == 8
                && IndustrialProgression.completed(state, "industrial-electronics") > 0
                && IndustrialProgression.completed(state, "industrial-alloys") > 0;
    }
    private IndustrialLogistics() {}
}
