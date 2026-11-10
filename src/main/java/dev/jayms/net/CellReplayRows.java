package dev.jayms.net;

import java.util.Arrays;

/** Private immutable canonical rows shared by staging and every installed history. */
final class CellReplayRows {
    private static final int[] START={0,1,9,73,585};
    final String cell;
    final int x,y,z,rawCount,size;
    private final int[] rows;
    private final short[] lookup;
    private final Protocol.Edit uniform;
    private CellReplayRows(Protocol.CellState state,int[] rows,int count,int rawCount,Protocol.Edit last) {
        x=state.x();y=state.y();z=state.z();cell=x+","+y+","+z;size=count;this.rawCount=rawCount;
        if(count==1){uniform=last;this.rows=null;lookup=null;return;}
        uniform=null;this.rows=Arrays.copyOf(rows,count*3);
        lookup=count>32?new short[4681]:null;
        if(lookup!=null)for(int i=0;i<count;i++)lookup[address(local(i))]=(short)(i+1);
    }
    static CellReplayRows prepare(Protocol.CellState state) {
        if(state.tree().size()!=16)throw new IllegalArgumentException("Invalid world cell resolution");
        int leaves=state.tree().leafCount();
        if(leaves<0||leaves>4096)throw new IllegalArgumentException("Invalid world cell leaf count");
        var rows=new int[(1+leaves)*3];int[] count={0},raw={0};Protocol.Edit[] last={null};
        state.forEachEdit(edit->{
            int slot=edit.depth()==0?0:count[0];
            if(slot*3>=rows.length)throw new IllegalArgumentException("World cell changed during replay preparation");
            rows[slot*3]=edit.type();rows[slot*3+1]=edit.color();
            rows[slot*3+2]=(edit.depth()<<12)|(edit.ix()<<8)|(edit.iy()<<4)|edit.iz();
            if(slot==count[0])count[0]++;raw[0]++;last[0]=edit;
        });
        if(raw[0]!=leaves+1)throw new IllegalArgumentException("World cell changed during replay preparation");
        return new CellReplayRows(state,rows,count[0],raw[0],last[0]);
    }
    private int local(int i){return rows[i*3+2];}
    Protocol.Edit edit(int i) {
        if(uniform!=null)return uniform;
        int local=local(i);
        return new Protocol.Edit(x,y,z,rows[i*3],local>>>12,(local>>>8)&15,(local>>>4)&15,local&15,rows[i*3+1]);
    }
    Protocol.Edit rawEdit(int i) {
        if(i==0)return uniform!=null&&rawCount==1?uniform:new Protocol.Edit(x,y,z,0);
        return edit(size==1?0:i);
    }
    String key(int i) {
        if(uniform!=null)return cell;
        int local=local(i),depth=local>>>12;
        return depth==0?cell:cell+"/"+depth+"/"+((local>>>8)&15)+"/"+((local>>>4)&15)+"/"+(local&15);
    }
    private static int address(int local) {
        int d=local>>>12;return START[d]+((local>>>8)&15)+((local&15)<<d)+(((local>>>4)&15)<<(2*d));
    }
    int find(String key) {
        if(key.equals(cell))return 0;
        if(uniform!=null||!key.startsWith(cell)||key.length()<=cell.length()||key.charAt(cell.length())!='/')return -1;
        try {
            int p=cell.length()+1,a=key.indexOf('/',p),b=a<0?-1:key.indexOf('/',a+1),c=b<0?-1:key.indexOf('/',b+1);
            if(a<0||b<0||c<0||key.indexOf('/',c+1)>=0)return -1;
            int d=Integer.parseInt(key,p,a,10),ix=Integer.parseInt(key,a+1,b,10),iy=Integer.parseInt(key,b+1,c,10),iz=Integer.parseInt(key,c+1,key.length(),10);
            if(d<1||d>4||ix<0||iy<0||iz<0||ix>=(1<<d)||iy>=(1<<d)||iz>=(1<<d))return -1;
            int local=(d<<12)|(ix<<8)|(iy<<4)|iz;
            int slot=-1;
            if(lookup!=null)slot=(lookup[address(local)]&65535)-1;
            else for(int i=0;i<size;i++)if(local(i)==local){slot=i;break;}
            // Map keys have exact String equality, including numeric spelling.
            return slot>=0&&key(slot).equals(key)?slot:-1;
        } catch(NumberFormatException invalid){return -1;}
    }
}
