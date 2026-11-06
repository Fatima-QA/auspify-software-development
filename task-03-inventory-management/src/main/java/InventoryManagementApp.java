import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class InventoryManagementApp extends JFrame {
    private final InventoryRepository repository = new InventoryRepository();
    private final JTextField searchField = new JTextField(14);
    private final JTextField categoryField = new JTextField(12);

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"SKU", "Name", "Category", "Stock", "Price (AZN)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public InventoryManagementApp() throws SQLException {
        super("Inventory Management System");
        repository.initialize();

        JPanel filters = new JPanel();
        filters.add(new JLabel("Name or SKU:"));
        filters.add(searchField);
        filters.add(new JLabel("Category:"));
        filters.add(categoryField);
        addButton(filters, "Search", this::refreshTable);
        addButton(filters, "Show All", () -> {
            searchField.setText("");
            categoryField.setText("");
            refreshTable();
        });

        JPanel actions = new JPanel();
        addButton(actions, "Add Product", this::addProduct);
        addButton(actions, "Increase Stock", () -> changeStock(1));
        addButton(actions, "Decrease Stock", () -> changeStock(-1));
        addButton(actions, "Inventory Report", () ->
                JOptionPane.showMessageDialog(this, repository.getInventoryReport()));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(filters, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(850, 450);
        setLocationRelativeTo(null);
        refreshTable();
    }

    private void refreshTable() throws SQLException {
        var products = repository.findProducts(
                searchField.getText(), categoryField.getText());
        model.setRowCount(0);

        for (Product product : products) {
            model.addRow(new Object[]{
                    product.sku(), product.name(), product.category(),
                    product.quantity(), product.unitPrice().toPlainString()
            });
        }
    }

    private void addProduct() throws SQLException {
        JTextField sku = new JTextField();
        JTextField name = new JTextField();
        JTextField category = new JTextField();
        JTextField quantity = new JTextField("0");
        JTextField price = new JTextField("0.00");

        Object[] form = {
                "SKU:", sku, "Name:", name, "Category:", category,
                "Initial stock:", quantity, "Unit price (AZN):", price
        };

        if (JOptionPane.showConfirmDialog(this, form, "Add Product",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }

        int units;
        BigDecimal unitPrice;
        try {
            units = Integer.parseInt(quantity.getText().trim());
            unitPrice = new BigDecimal(price.getText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Enter a whole number for stock and a price such as 12.50.");
        }

        repository.addProduct(sku.getText(), name.getText(), category.getText(),
                units, unitPrice);
        searchField.setText("");
        categoryField.setText("");
        refreshTable();
    }

    private void changeStock(int direction) throws SQLException {
        int row = table.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a product first.");
        }

        String sku = (String) table.getValueAt(row, 0);
        String input = JOptionPane.showInputDialog(this, "Enter the number of units:");
        if (input == null) return;

        int units;
        try {
            units = Integer.parseInt(input.trim());
            if (units <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a positive whole number.");
        }

        if (!repository.adjustStock(sku, direction * units)) {
            throw new IllegalArgumentException(
                    "Stock could not be updated. Check the available quantity.");
        }
        refreshTable();
    }

    private void addButton(JPanel panel, String label, SqlAction action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> {
            try {
                action.run();
            } catch (SQLException | IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(button);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new InventoryManagementApp().setVisible(true);
            } catch (SQLException exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}