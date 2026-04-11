import raynna.tools.itemeditor.ItemDefinitionsService;
import java.nio.file.Paths;
public class TmpTextureIds {
  public static void main(String[] args) {
    ItemDefinitionsService s = new ItemDefinitionsService(Paths.get("C:/Users/andre/Desktop/727-source/data/cache"));
    var ids = s.listTextureIds();
    System.out.println("count=" + ids.size());
    for (int i = 0; i < Math.min(80, ids.size()); i++) {
      System.out.println(i + ":" + ids.get(i));
    }
  }
}
