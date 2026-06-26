package net.creeperhost.minetogethercommunity.modulargui;

import java.util.function.DoubleSupplier;

public class Constraint {

    private final DoubleSupplier supplier;

    private Constraint(DoubleSupplier supplier) {
        this.supplier = supplier;
    }

    public double get() {
        return supplier.getAsDouble();
    }

    public static Constraint literal(double value) {
        return new Constraint(() -> value);
    }

    public static Constraint dynamic(DoubleSupplier supplier) {
        return new Constraint(supplier);
    }
}
