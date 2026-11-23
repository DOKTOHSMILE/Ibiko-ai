package ai.ibiko.ibiko1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Contrast;
import ai.ibiko.ibiko1.formulas.Kinds;

/** "Is a frog an animal?" worked out by going up what each thing is. */
public class KindsTest {
    private static final Map<String, String> READ = new HashMap<String, String>();

    static {
        READ.put("frog", "A frog is an amphibian with smooth skin.");
        READ.put("amphibian", "Amphibians are vertebrates that live in water and on land.");
        READ.put("vertebrate", "A vertebrate is an animal with a backbone.");
        READ.put("animal", "An animal is a living thing that eats.");
        READ.put("loop", "A loop is a thing that is a loop.");
        READ.put("penguin", "Penguins are seabirds that cannot fly.");
        READ.put("seabird", "Seabirds are birds that live at sea.");
    }

    private static List<Contrast.Kind> whatIs(String name) {
        String key = name.toLowerCase().replaceFirst("s$", "");
        String t = READ.get(key);
        return t == null ? null : Contrast.kinds(t, key);
    }

    @Test
    public void readsTheQuestion() {
        assertArrayEquals(new String[] {"frog", "animal"}, Kinds.asked("Is a frog an animal?"));
        assertArrayEquals(new String[] {"tomato", "fruit"}, Kinds.asked("Is a tomato a kind of fruit?"));
        assertArrayEquals(new String[] {"whales", "mammals"}, Kinds.asked("Are whales mammals?"));
        assertArrayEquals(new String[] {"the sun", "star"}, Kinds.asked("Is the sun a star?"));
        assertNull(Kinds.asked("Is it raining?"));
        assertNull(Kinds.asked("What is a frog?"));
    }

    @Test
    public void goesUpWhatEachThingIs() {
        List<Kinds.Step> chain = Kinds.chain("frog", "animal", KindsTest::whatIs);
        assertEquals(3, chain.size());
        assertEquals("Yes. A frog is an amphibian; amphibians are vertebrates; a vertebrate is an animal.", Kinds.said(chain));
        assertEquals("Yes. Penguins are seabirds; seabirds are birds.", Kinds.said(Kinds.chain("penguin", "bird", KindsTest::whatIs)));
        assertEquals("one step", 1, Kinds.chain("frog", "amphibian", KindsTest::whatIs).size());
    }

    @Test
    public void everyKindTheOpeningGivesIsUsed() {
        READ.put("mammal", "Mammals are in the class Mammalia. They are a group of vertebrate animals. They have fur.");
        READ.put("dog", "Dogs are mammals, usually to be kept as pets.");
        READ.put("toad", "Toads are amphibians and vertebrates of the order Anura.");
        READ.put("gold", "Gold is a soft, dense, bright yellow metal. It is a chemical element.");
        assertEquals("Yes. Dogs are mammals; mammals are a group of vertebrate animals.", Kinds.said(Kinds.chain("dog", "animal", KindsTest::whatIs)));
        assertEquals("Yes. Toads are vertebrates.", Kinds.said(Kinds.chain("toad", "vertebrate", KindsTest::whatIs)));
        assertEquals("Yes. Gold is a soft, dense, bright yellow metal.", Kinds.said(Kinds.chain("gold", "metal", KindsTest::whatIs)));
        assertEquals("Yes. Gold is a chemical element.", Kinds.said(Kinds.chain("gold", "element", KindsTest::whatIs)));
    }

    @Test
    public void notReachingItIsNotANo() {
        assertNull("not read is not false: nothing is said", Kinds.chain("frog", "plant", KindsTest::whatIs));
        assertNull(Kinds.chain("loop", "animal", KindsTest::whatIs));
        assertNull(Kinds.chain("unknown", "animal", KindsTest::whatIs));
    }
}
