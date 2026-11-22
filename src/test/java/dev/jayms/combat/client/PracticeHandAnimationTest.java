package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PracticeHandAnimationTest {
    @Test
    void swingStartsOnTheClickRestartsHalfwayAndFinishesAfterSixTicks() {
        var hand = sword();
        hand.attack(Item.SWORD, false, ms(10));
        assertEquals(0, pose(hand, Item.SWORD, 10).swing());
        hand.attack(Item.SWORD, false, ms(159));
        assertEquals(149f / 300, pose(hand, Item.SWORD, 159).swing(), .0001f);
        hand.attack(Item.SWORD, false, ms(160));
        assertEquals(0, pose(hand, Item.SWORD, 160).swing());
        assertEquals(.75, pose(hand, Item.SWORD, 385).swing(), .0001);
        assertEquals(1, pose(hand, Item.SWORD, 460).swing());
    }

    @Test
    void guardSuppressesSwingOnlyWithTheSelectedAndDisplayedSword() {
        var hand = sword();
        hand.attack(Item.SWORD, true, ms(20));
        var guard = hand.pose(Item.SWORD, true, false, ms(21));
        assertTrue(guard.blocking());
        assertEquals(1, guard.swing());
        assertFalse(pose(hand, Item.SWORD, 22).blocking());
        assertEquals(1, pose(hand, Item.SWORD, 22).swing());

        hand.select(Item.FOOD, ms(25));
        assertFalse(hand.pose(Item.FOOD, true, false, ms(26)).blocking());
        var food = hand.pose(Item.FOOD, true, false, ms(150));
        assertEquals(Item.FOOD, food.item());
        assertFalse(food.blocking());
        assertEquals(1, food.swing());
    }

    @Test
    void pearlUsePulseDoesNotRequireAnItemChangeAndHeldUseDoesNotRepeatIt() {
        var hand = new PracticeHandAnimation();
        hand.reset(Item.PEARL, ms(0));
        hand.use(Item.PEARL, ms(10));
        var recoil = hand.pose(Item.PEARL, true, false, ms(10));
        assertEquals(Item.PEARL, recoil.item());
        assertEquals(.17f, recoil.equipDrop());
        assertEquals(1, recoil.swing());
        assertFalse(recoil.blocking());
        assertEquals(.0425f, hand.pose(Item.PEARL, true, false, ms(90)).equipDrop(), .0001);
        assertEquals(0, hand.pose(Item.PEARL, true, false, ms(170)).equipDrop());
        assertEquals(0, hand.pose(Item.PEARL, true, false, ms(220)).equipDrop());
        hand.use(Item.PEARL, ms(250));
        assertEquals(.17f, pose(hand, Item.PEARL, 250).equipDrop());
    }

    @Test
    void genericAttackRetainsSwingDuringAThrowButHeldConsumptionSuppressesIt() {
        var hand = new PracticeHandAnimation();
        hand.reset(Item.PEARL, ms(0));
        hand.attack(Item.PEARL, true, ms(10));
        hand.use(Item.PEARL, ms(20));
        var combined = hand.pose(Item.PEARL, true, false, ms(70));
        assertEquals(.2f, combined.swing(), .0001);
        assertTrue(combined.equipDrop() > 0);
        assertFalse(combined.blocking());

        for (Item item : new Item[]{Item.FOOD, Item.SPEED, Item.FIRE_RESISTANCE, Item.BOW}) {
            hand.reset(item, ms(0));
            hand.use(item, ms(10));
            hand.attack(item, true, ms(20));
            assertEquals(1, hand.pose(item, true, false, ms(30)).swing());
            hand.attack(item, false, ms(40));
            assertEquals(.2f, pose(hand, item, 100).swing(), .0001);
        }
    }

    @Test
    void bowRaisesImmediatelyAndCannotPredictMoreThanTwoTicksAheadOfTheServer() {
        var hand = new PracticeHandAnimation();
        hand.reset(Item.BOW, ms(0));
        hand.use(Item.BOW, ms(10));
        assertEquals(1, hand.pose(Item.BOW, true, false, 0, true, true, ms(10)).bowDrawTicks());
        assertEquals(2, hand.pose(Item.BOW, true, false, 0, true, true, ms(510)).bowDrawTicks());
        assertEquals(11, hand.pose(Item.BOW, true, false, 9, true, true, ms(510)).bowDrawTicks());
        assertEquals(20, hand.pose(Item.BOW, true, false, 20, true, true, ms(1050)).bowDrawTicks());
        assertEquals(0, hand.pose(Item.BOW, false, false, 20, true, true, ms(1051)).bowDrawTicks());
        assertEquals(0, hand.pose(Item.BOW, true, false, 0, false, true, ms(1052)).bowDrawTicks());
        assertEquals(20, hand.pose(Item.BOW, true, false, 0, true, false, ms(1053)).bowDrawTicks());
    }

    @Test
    void usePulseDoesNotDelayEquipmentOrReplaceSwordGuard() {
        var hand = new PracticeHandAnimation();
        hand.reset(Item.PEARL, ms(0));
        hand.use(Item.PEARL, ms(10));
        hand.select(Item.SWORD, ms(12));
        var lowering = hand.pose(Item.SWORD, true, false, ms(20));
        assertEquals(Item.PEARL, lowering.item());
        assertEquals(8f / 125, lowering.equipDrop(), .0001);
        assertFalse(lowering.blocking());
        hand.use(Item.SWORD, ms(137));
        var switched = hand.pose(Item.SWORD, true, false, ms(137));
        assertEquals(Item.SWORD, switched.item());
        assertTrue(switched.blocking());
        assertEquals(1, switched.equipDrop());
        assertEquals(0, hand.pose(Item.SWORD, true, false, ms(262)).equipDrop());
    }

    @Test
    void equipmentChangesAtBottomAndRepeatedSelectionKeepsTheTimeline() {
        var hand = sword();
        hand.select(Item.HEALING, ms(0));
        var lowering = pose(hand, Item.HEALING, 50);
        assertEquals(Item.SWORD, lowering.item());
        assertEquals(.4f, lowering.equipDrop());
        hand.select(Item.HEALING, ms(100));
        assertEquals(Item.SWORD, pose(hand, Item.HEALING, 124).item());
        var switchPoint = pose(hand, Item.HEALING, 125);
        assertEquals(Item.HEALING, switchPoint.item());
        assertEquals(1, switchPoint.equipDrop());
        assertEquals(.4f, pose(hand, Item.HEALING, 200).equipDrop());
        assertEquals(0, pose(hand, Item.HEALING, 250).equipDrop());
        assertEquals(0, pose(hand, Item.HEALING, 500).equipDrop());
    }

    @Test
    void wheelScrollCanReplaceOrCancelAPendingItem() {
        var hand = sword();
        hand.select(Item.FOOD, ms(0));
        hand.select(Item.HEALING, ms(60));
        assertEquals(Item.HEALING, pose(hand, Item.HEALING, 125).item());

        hand.reset(Item.SWORD, ms(0));
        hand.select(Item.FOOD, ms(0));
        hand.select(Item.SWORD, ms(60));
        assertEquals(.48f, pose(hand, Item.SWORD, 60).equipDrop());
        assertEquals(Item.SWORD, pose(hand, Item.SWORD, 120).item());
        assertEquals(0, pose(hand, Item.SWORD, 120).equipDrop());
    }

    @Test
    void coarseAndFineFramesProduceSameEquipSwingAndGaitPose() {
        var coarse = sword();
        var fine = sword();
        for (var hand : new PracticeHandAnimation[]{coarse, fine}) {
            hand.attack(Item.SWORD, false, ms(0));
            hand.select(Item.FOOD, ms(100));
        }
        for (int time = 100; time < 245; time += 5)
            fine.pose(Item.FOOD, false, true, ms(time));
        var a = coarse.pose(Item.FOOD, false, true, ms(245));
        var b = fine.pose(Item.FOOD, false, true, ms(245));
        assertEquals(a.item(), b.item());
        assertEquals(a.swing(), b.swing());
        assertEquals(a.equipDrop(), b.equipDrop());
        assertEquals(a.walkPhase(), b.walkPhase(), .0001);
        assertEquals(a.walkAmount(), b.walkAmount(), .0001);
    }

    private static PracticeHandAnimation sword() {
        var hand = new PracticeHandAnimation();
        hand.reset(Item.SWORD, ms(0));
        return hand;
    }

    private static PracticeHandAnimation.Pose pose(PracticeHandAnimation hand, Item item, int time) {
        return hand.pose(item, false, false, ms(time));
    }

    private static long ms(int time) { return time * 1_000_000L; }
}
