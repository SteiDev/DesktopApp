import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.ssd.project.Main;

public class MainTest {
    @Test
    public void testReturnTrue() {
        assertTrue(Main.returnTrue());
    }

    @Test
    public void testReturnTrue2() {
        assertEquals(true, Main.returnTrue());
    }

    //@Test
    public void testReturnTrue3() {
        assertEquals(false, Main.returnTrue());
    }
}
