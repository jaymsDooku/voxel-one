package dev.jayms.net;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LongLruCacheTest {
 @Test void matchesAccessOrderReferenceAcrossHitsEvictionsUpdatesAndClear(){
  for(int capacity:new int[]{1,2,3,16,127,1024}){
   var actual=new LongLruCache<String>(capacity);
   var expected=new LinkedHashMap<Long,String>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,String> e){return size()>capacity;}};
   var random=new Random(748291);
   for(int i=0;i<30000;i++){
    long key=switch(i%11){case 0->Long.MIN_VALUE;case 1->Long.MAX_VALUE;case 2->0;default->random.nextInt(capacity*3)-capacity;};
    if(i%997==0){actual.clear();expected.clear();}
    if(random.nextBoolean()){String value="value"+i;actual.put(key,value);expected.put(key,value);}
    else assertEquals(expected.get(key),actual.get(key),"capacity="+capacity+" operation="+i);
    assertEquals(expected.size(),actual.size());
   }
  }
 }
 @Test void boundsAndNullContract(){assertThrows(IllegalArgumentException.class,()->new LongLruCache<>(0));var cache=new LongLruCache<String>(2);assertThrows(NullPointerException.class,()->cache.put(1,null));assertEquals(0,cache.size());}
}
