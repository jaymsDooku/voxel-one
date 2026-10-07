package dev.jayms.net.mobile;

import java.util.*;

/** Small bounded JSON codec for the mobile transport; no reflection or account serialization. */
public final class Json {
    private Json() {}
    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            var out = new StringBuilder("\"");
            for (char c : s.toCharArray()) switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
            return out.append('"').toString();
        }
        if (value instanceof Number n) {
            if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Nonfinite JSON number");
            return n.toString();
        }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> m) {
            var items = new ArrayList<String>();
            for (var e : m.entrySet()) items.add(write(e.getKey().toString()) + ":" + write(e.getValue()));
            return "{" + String.join(",", items) + "}";
        }
        if (value instanceof Collection<?> c) return "[" + String.join(",", c.stream().map(Json::write).toList()) + "]";
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    public static Map<String,Object> object(String text) {
        if (text.length() > 8192) throw new IllegalArgumentException("Request too large");
        var parser = new Parser(text); Object value = parser.value(0); parser.space();
        if (parser.pos != text.length() || !(value instanceof Map<?,?>)) throw new IllegalArgumentException("Invalid JSON object");
        @SuppressWarnings("unchecked") var result = (Map<String,Object>) value;
        return result;
    }
    public static String text(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof String s)) throw new IllegalArgumentException("Missing text");
        return s;
    }
    public static double number(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Missing number");
        return n.doubleValue();
    }
    public static int integer(Map<String,Object> m, String key) {
        double n = number(m,key);
        if (n != Math.rint(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid integer");
        return (int)n;
    }
    private static final class Parser {
        final String text; int pos;
        Parser(String text) { this.text=text; }
        void space() { while(pos<text.length() && " \r\n\t".indexOf(text.charAt(pos))>=0) pos++; }
        char take() { if(pos>=text.length()) throw new IllegalArgumentException("Invalid JSON"); return text.charAt(pos++); }
        boolean accept(char c) { space(); if(pos<text.length() && text.charAt(pos)==c) {pos++;return true;}return false; }
        void expect(char c) { if(!accept(c)) throw new IllegalArgumentException("Invalid JSON"); }
        Object value(int depth) {
            if(depth>8) throw new IllegalArgumentException("JSON too deep"); space();
            if(pos>=text.length()) throw new IllegalArgumentException("Invalid JSON"); char c=text.charAt(pos);
            if(c=='"') return string();
            if(c=='{') {
                pos++;var m=new LinkedHashMap<String,Object>(); if(accept('}'))return m;
                do {space();String k=string();expect(':');if(m.containsKey(k))throw new IllegalArgumentException("Duplicate key");m.put(k,value(depth+1));}while(accept(','));
                expect('}');return m;
            }
            if(c=='[') {
                pos++;var a=new ArrayList<Object>();if(accept(']'))return a;
                do {a.add(value(depth+1));if(a.size()>128)throw new IllegalArgumentException("Array too long");}while(accept(','));expect(']');return a;
            }
            for(String literal:List.of("true","false","null"))if(text.startsWith(literal,pos)){pos+=literal.length();return literal.equals("null")?null:literal.equals("true");}
            int start=pos;while(pos<text.length() && "-+0123456789.eE".indexOf(text.charAt(pos))>=0)pos++;
            String n=text.substring(start,pos);
            if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Invalid number");
            double d=Double.parseDouble(n);if(!Double.isFinite(d))throw new IllegalArgumentException("Invalid number");return d;
        }
        String string() {
            if(take()!='"')throw new IllegalArgumentException("Invalid string");var out=new StringBuilder();
            for(;;){char c=take();if(c=='"')return out.toString();if(c<32)throw new IllegalArgumentException("Invalid string");
                if(c=='\\')switch(take()){
                    case '"'->out.append('"');case '\\'->out.append('\\');case '/'->out.append('/');case 'n'->out.append('\n');case 'r'->out.append('\r');case 't'->out.append('\t');case 'b'->out.append('\b');case 'f'->out.append('\f');
                    case 'u'->{if(pos+4>text.length())throw new IllegalArgumentException("Invalid escape");out.append((char)Integer.parseInt(text.substring(pos,pos+4),16));pos+=4;}
                    default->throw new IllegalArgumentException("Invalid escape");
                }else out.append(c);
            }
        }
    }
}
