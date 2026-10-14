package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryAccountingWorkflowTest {
    double total(CitySimulation s,String id) {
        return s.history.query(null,1,List.of(id),0,s.frame().elapsed()+1,2,1000).series().get(0).points().stream().filter(p->!p.gap()).mapToDouble(CityHistory.Point::value).sum();
    }
    @Test void actualLandSaleProductionPaidTransferAndLocationAccounting() {
        var ground=new CityTest.Ground();var s=new CitySimulation(new GameConfig(true,true,60,0),ground,ground.terrain,null,ProductionCatalog.toolEra());s.collectHistory();
        double before=s.economy.landRevenue;assertNotNull(s.economy.buyPlot(1,0,12,30,28));double land=s.economy.landRevenue-before;assertTrue(land>0);
        var seller=s.economy.companies().stream().filter(c->c.kind==CityMaterials.LOGGING).findFirst().orElseThrow();
        var buyer=s.economy.companies().stream().filter(c->c.kind==CityMaterials.MASONRY).findFirst().orElseThrow();
        s.economy.resources.produced(CityEconomy.COMPANY,seller.id,Blocks.WOOD,2*CityMaterials.UNIT);
        for(var stock:s.economy.resources.state().stocks())if(stock.material()==Blocks.WOOD&&stock.ownerKind()==CityEconomy.COMPANY&&stock.owner()!=seller.id)s.economy.resources.remove(stock.ownerKind(),stock.owner(),stock.material(),stock.units());
        double receipts=seller.receipts;
        assertTrue(s.economy.purchase(buyer.id,Blocks.WOOD,CityMaterials.UNIT));
        double revenue=seller.receipts-receipts;assertTrue(revenue>0);
        s.economy.businesses.wage(s.economy.account(seller.id),2,1);
        s.economy.businesses.rent(s.economy.account(seller.id),1);
        s.advance(.1);
        assertEquals(s.economy.landRevenue-before,total(s,"finance.land"),1e-6);
        assertEquals(2,total(s,"resource."+Blocks.WOOD+".production"),1e-6);
        assertTrue(total(s,"resource."+Blocks.WOOD+".sales")>=1);
        var accounts=s.economy.businesses.records().stream().filter(a->a.company()==seller.id).toList();
        assertEquals(accounts.stream().mapToDouble(a->a.total().revenue()).sum(),total(s,"company."+seller.id+".revenue"),1e-6);
        assertEquals(accounts.stream().mapToDouble(a->a.total().expenses()).sum(),total(s,"company."+seller.id+".costs"),1e-6);
        assertEquals(accounts.stream().mapToDouble(a->a.total().profit()).sum(),total(s,"company."+seller.id+".profit"),1e-6);
        assertEquals(s.economy.businesses.records().stream().mapToDouble(a->a.total().revenue()).sum(),total(s,"business.revenue"),1e-6); // Locations are not added again.
    }
}
