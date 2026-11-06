import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.sql.PreparedStatement;

public class InventoryRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:inventory.db";

    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY,
                    sku TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    quantity INTEGER NOT NULL CHECK (quantity >= 0),
                    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
                )
                """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    public void addProduct(String sku, String name, String category,
                           int quantity, BigDecimal unitPrice) throws SQLException {
        if (sku.isBlank() || name.isBlank() || category.isBlank()) {
            throw new IllegalArgumentException("SKU, name and category are required.");
        }
        if (quantity < 0 || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Quantity and price cannot be negative.");
        }

        long priceInCents;
        try {
            priceInCents = unitPrice.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Enter a price within range and with no fractions of a cent.");
        }

        String sql = """
            
                INSERT INTO products (sku, name, category, quantity, unit_price_cents)
                            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sku.trim());
            statement.setString(2, name.trim());
            statement.setString(3, category.trim());
            statement.setInt(4, quantity);
            statement.setLong(5, priceInCents);
            statement.executeUpdate();
        }
    }


    public Product findProductBySku(String sku) throws SQLException {
        String sql =
                """
            SELECT id, sku, name, category, quantity,
                unit_p
                          FRO
                          WHERE sku = ?
            """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sku.trim());

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }

                return new Product(
                        rows.getInt("id"),
                        rows.getString("sku"),
                        rows.getString("name"),
                        rows.getString("category"),
                        rows.getInt("quantity"),
                        BigDecimal.valueOf(rows.getLong("unit_price_cents"), 2)
                );
            }
        }
    }


    public boolean adjustStock(String sku, int change) throws SQLException {
        if (change == 0) {
            throw new IllegalArgumentException("Stock change cannot be zero.");
        }

        String sql = """
        UPDATE products
        SET quantity = quantity + ?
        WHERE sku = ?
          AND quantity + ? BETWEEN 0 AND 2147483647
        """;


        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, change);
            statement.setString(2, sku.trim());
            statement.setInt(3, change);


    return statement.executeUpdate() == 1;
        }
    }



    public java.util.List<Product> findProducts(String keyword, String category)
                throws SQLException {
        String sql = """
            
                SELECT id, sku, name, category, quantity, unit_price_cents
                               FROM products
            WHERE (instr(lower(name),
                lower(?)) > 0
                   OR instr(lower
                (sku), lower(?))
                > 0)
              AND (? = '' OR category = ? COLLATE NOCASE)
            ORDER BY name, id
            """;

        var products = new java.util.ArrayList<Product>();

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword.trim());
            statement.setString(2, keyword.trim());
            statement.setString(3, category.trim());
            statement.setString(4, category.trim());

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    products.add(new Product(
                            rows.getInt("id"),
                            rows.getString("sku"),
                            rows.getString("name"),
                            rows.getString("category"),
                            rows.getInt("quantity"),
                            BigDecimal.valueOf(rows.getLong("unit_price_cents"), 2)
                    ));
                }
            }
        }

        return products;
    }


    public String getInventoryReport() throws SQLException {
        var products = findProducts("", "");
        long totalUnits = 0;
        BigDecimal totalValue = new BigDecimal("0.00");

        for (Product product : products) {
            totalUnits += product.quantity();
            totalValue = totalValue.add(
                    product.unitPrice().
                multiply(BigDecimal.valueOf
                (product.
                quantity()))
            );
        }

        return String.
                format("""
            === Inventory Report ===
            Products: %d
            Total units in stock: %d
            Total stock value: %s AZN
            """, products.size(), totalUnits, totalValue.toPlainString());
    }

    public static void main(String[] args) throws SQLException {
        InventoryRepository repository = new InventoryRepository();
        repository.initialize();
        System.out.print(repository.getInventoryReport());
    }
    }