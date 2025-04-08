package net.Realism.compat;

public class isModLoaded {

    public static boolean isTramwaysLoaded() {
        try {
            Class.forName("purplecreate.tramways.Tramways");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
