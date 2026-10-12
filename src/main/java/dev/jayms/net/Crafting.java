package dev.jayms.net;

import java.util.*;
import java.util.stream.Collectors;

/** Sandbox catalog. IDs are explicit and independent from book order. */
public final class Crafting {
    public static final int MAX_BATCH=64, MAX_DROPS=100000;
    public static final List<String> CATEGORIES=List.of("All","Tools","Materials","Building","Decorations","Lighting");
    public record Recipe(int id,String name,Map<Integer,Integer> inputs,int output,int count,
                         String category,String group,List<String> diagram) {
        public Recipe {
            inputs=Collections.unmodifiableMap(new TreeMap<>(inputs));
            diagram=List.copyOf(diagram);
        }
        public Recipe(int id,String name,Map<Integer,Integer> inputs,int output,int count) {
            this(id,name,inputs,output,count,Items.category(output),"",List.of());
        }
        public Map<Integer,Integer> costs(int quantity) {
            if(quantity<1||quantity>MAX_BATCH) throw new IllegalArgumentException("Batch quantity must be 1-64.");
            Map<Integer,Integer> result=new TreeMap<>();
            inputs.forEach((item,n)->result.put(item,Math.multiplyExact(n,quantity)));
            return Collections.unmodifiableMap(result);
        }
        public int maximum(Inventory inv) {
            int n=MAX_BATCH;
            for(var e:inputs.entrySet()) n=Math.min(n,inv.countType(e.getKey())/e.getValue());
            return n;
        }
        public boolean available(Inventory inv) { return maximum(inv)>0; }
        public String ingredients(Inventory inv) { return ingredients(inv,1); }
        public String ingredients(Inventory inv,int quantity) {
            return costs(quantity).entrySet().stream().map(e->Items.name(e.getKey())+" "+inv.countType(e.getKey())+"/"+e.getValue()).collect(Collectors.joining(" | "));
        }
        public String missing(Inventory inv,int quantity) {
            return costs(quantity).entrySet().stream().filter(e->inv.countType(e.getKey())<e.getValue())
                    .map(e->Items.name(e.getKey())+" "+(e.getValue()-inv.countType(e.getKey()))).collect(Collectors.joining(", "));
        }
        public String variant() { return Items.tool(output)?(output>=195?"Stone head":"Wooden head"):Items.name(Blocks.material(output)); }
    }
    public record Result(boolean accepted,Inventory inventory,int output,int excess,String message) {}
    private static final List<Recipe> RECIPES;
    private static final Map<Integer,Recipe> BY_ID;
    private static final Map<Integer,String> SEARCH;
    static {
        List<Recipe> r=new ArrayList<>();
        // Legacy IDs: material ordinal * 8 + (depth - 1) * 2 + cut/join.
        int[] materials={1,2,3,4,5,6,7,165,166,167,180};
        for(int m=0;m<materials.length;m++) for(int d=1;d<=4;d++) {
            int parent=Blocks.piece(materials[m],d-1), child=Blocks.piece(materials[m],d), id=m*8+(d-1)*2;
            r.add(new Recipe(id,"Cut "+Items.name(parent),Map.of(parent,1),child,8,Items.category(child),Items.name(materials[m]),List.of()));
            r.add(new Recipe(id+1,"Join "+Items.name(child),Map.of(child,8),parent,1,Items.category(parent),Items.name(materials[m]),List.of()));
        }
        r.add(new Recipe(88,"Saw planks",Map.of(6,1),165,4,"Materials","",List.of()));
        r.add(new Recipe(89,"Make bricks",Map.of(3,4),166,4));
        r.add(new Recipe(90,"Make glass",Map.of(4,4),167,4));
        r.add(new Recipe(91,"Grow grass",Map.of(2,1,7,1),1,1));
        r.add(new Recipe(92,"Compost grass",Map.of(1,1),2,1));
        r.add(new Recipe(93,"Flower pot",Map.of(4,3,2,1,7,1),8,1));
        r.add(new Recipe(94,"LED light",Map.of(167,1,3,1),180,1));
        r.add(new Recipe(95,"Make sticks",Map.of(165,2),190,4));
        int[] heads={3,2,1,3}, sticks={2,1,2,2};
        List<List<String>> diagrams=List.of(List.of("HHH"," S "," S "),List.of(" H "," H "," S "),List.of(" H "," S "," S "),List.of("HH ","HS "," S "));
        for(int i=0;i<8;i++) r.add(new Recipe(96+i,Items.name(191+i),Map.of(i<4?165:3,heads[i%4],190,sticks[i%4]),191+i,1,"Tools","",diagrams.get(i%4)));
        validateCatalog(r);
        BY_ID=r.stream().collect(Collectors.toUnmodifiableMap(Recipe::id,x->x));
        // Tools first, goods second, folded material recipes last.
        r.sort(Comparator.comparingInt(x->x.id()>=96?0:x.group().isEmpty()?1:2));
        RECIPES=List.copyOf(r);
        SEARCH=r.stream().collect(Collectors.toUnmodifiableMap(Recipe::id,x->(x.name()+" "+Items.name(x.output())+" "+x.inputs().keySet().stream().map(Items::name).collect(Collectors.joining(" "))).toLowerCase(Locale.ROOT)));
    }
    /** Definition format is the Recipe record; applications validate a complete catalog before use. */
    public static void validateCatalog(Collection<Recipe> recipes) {
        Set<Integer> ids=new HashSet<>();
        for(var r:recipes) {
            if(r.id()<0||!ids.add(r.id())||r.name()==null||r.name().isBlank()||!Items.valid(r.output())||r.output()==0||r.count()<=0||r.inputs().isEmpty()) throw new IllegalArgumentException("Invalid or duplicate recipe");
            if(!CATEGORIES.contains(r.category())||r.group()==null) throw new IllegalArgumentException("Invalid recipe metadata");
            Math.multiplyExact(r.count(),MAX_BATCH);
            for(var e:r.inputs().entrySet()) {
                if(e.getKey()==0||!Items.valid(e.getKey())||e.getValue()<=0) throw new IllegalArgumentException("Invalid ingredient");
                Math.multiplyExact(e.getValue(),MAX_BATCH);
            }
            if(!r.diagram().isEmpty()) {
                if(r.diagram().size()!=3||r.diagram().stream().anyMatch(row->row.length()!=3||!row.matches("[ HS]{3}"))||!Items.tool(r.output())) throw new IllegalArgumentException("Invalid diagram");
                int material=r.output()>=195?3:165;
                long h=r.diagram().stream().flatMapToInt(String::chars).filter(c->c=='H').count(), s=r.diagram().stream().flatMapToInt(String::chars).filter(c->c=='S').count();
                if(r.inputs().size()!=2||r.inputs().getOrDefault(material,0)!=h||r.inputs().getOrDefault(190,0)!=s) throw new IllegalArgumentException("Diagram costs disagree");
            }
        }
    }
    public static List<Recipe> recipes() { return RECIPES; }
    public static Recipe recipe(int id) { return BY_ID.get(id); }
    public static boolean matches(Recipe r,String query) { return SEARCH.get(r.id()).contains(query.toLowerCase(Locale.ROOT).trim()); }
    public static Recipe ingredientRecipe(int item) {
        var processing=RECIPES.stream().filter(r->r.output()==item&&r.group().isEmpty()).findFirst().orElse(null);
        if(processing!=null)return processing;
        // Raw terrain materials prefer their harvest source. Fractional ingredients link to a cut.
        return Blocks.isPiece(item)?RECIPES.stream().filter(r->r.output()==item&&r.count()==8).findFirst().orElse(null):null;
    }
    public static int dropSlots(int type,int count) { return count==0?0:(count-1)/Items.stackLimit(type)+1; }
    public static Result prepare(Inventory inv,int id) { return prepare(inv,id,1); }
    public static Result prepare(Inventory inv,int id,int quantity) { return prepare(inv,id,quantity,MAX_DROPS); }
    public static Result prepare(Inventory inv,int id,int quantity,int freeDropSlots) {
        Recipe r=recipe(id);
        if(r==null) return new Result(false,inv,0,0,"Unknown recipe.");
        if(quantity<1||quantity>MAX_BATCH) return new Result(false,inv,0,0,"Batch quantity must be 1-64.");
        try {
            String missing=r.missing(inv,quantity);
            if(!missing.isEmpty()) return new Result(false,inv,0,0,"Missing: "+missing+".");
            Inventory next=inv.copy();
            if(!next.consume(r.costs(quantity))) return new Result(false,inv,0,0,"Inventory changed; try again.");
            int output=Math.multiplyExact(r.count(),quantity), excess=next.add(r.output(),output);
            if(dropSlots(r.output(),excess)>freeDropSlots) return new Result(false,inv,0,0,"Too many ground items: collect some before crafting.");
            return new Result(true,next,r.output(),excess,"Crafted "+output+" "+Items.name(r.output())+"."+(excess>0?" "+excess+" dropped nearby.":""));
        } catch(ArithmeticException e) { return new Result(false,inv,0,0,"Recipe quantity overflow."); }
    }
    private Crafting() {}
}
