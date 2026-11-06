import java.math.BigDecimal;

public record Product(
        int id,
        String sku,
        String name,
        String category,
        int quantity,
        BigDecimal unitPrice
) {}