import raynna.tools.itemeditor.ItemDefinitionsService;
import com.jagex.CodexTextureProviderFactory;
import com.jagex.Interface_ma;
import com.jagex.Class53;
import java.nio.file.Paths;
import java.util.Arrays;
public class TmpTextureProbe {
  private static int hash(int[] a){ return Arrays.hashCode(a); }
  public static void main(String[] args) {
    ItemDefinitionsService s = new ItemDefinitionsService(Paths.get("C:/Users/andre/Desktop/727-source/data/cache"));
    Interface_ma p = CodexTextureProviderFactory.create(s);
    for (int id : new int[]{39,40,41,42}) {
      byte[] raw = s.loadTextureBytes(id);
      System.out.println("id="+id+" rawLen="+(raw==null?-1:raw.length)+" ok="+p.method170(id,(short)0));
      Class53 d = p.method174(id,0);
      if (d != null) {
        int size = d.aBoolean518 ? 64 : 128;
        int[] px = d.anInt528 == 2 ? p.method172(id,1.0f,size,size,false,(byte)0) : p.method171(id,1.0f,size,size,false,0);
        System.out.println("  size="+size+" type="+d.anInt528+" hash="+hash(px)+" first="+(px.length>0?px[0]:0));
      }
    }
  }
}
