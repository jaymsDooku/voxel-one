package dev.jayms;
import java.nio.file.*;
public class BaseCity8Fixture {
 public static void main(String[] args) throws Exception {
  var helper = new CityCapitalTest();
  var simulation = helper.sim(new CityTest.Ground());
  if (!simulation.command(helper.exchange(), 1, helper.pose).contains("built")) throw new AssertionError();
  helper.staff(simulation);
  var book = simulation.economy.capital.exchange;
  int company = simulation.economy.companies().get(0).id;
  var owner = book.listing(company).founder();
  var buyer = book.listing(simulation.economy.companies().get(1).id).founder();
  simulation.command(helper.capital(0, company, owner, 100, 500, 0), 1, helper.pose);
  simulation.command(helper.capital(1, company, buyer, 120, 500, 0), 1, helper.pose);
  var expected = simulation.frame();
  if (expected.economy().capital().book().orders().isEmpty()) throw new AssertionError();
  simulation.save(Path.of(args[0]));
  if (!expected.equals(dev.jayms.net.city.CitySimulation.load(Path.of(args[0])))) throw new AssertionError();
  System.out.println("Base CITY8 fixture generated with ownership, open orders and exchange building.");
 }
}