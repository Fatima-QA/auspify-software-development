import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.sql.SQLException;

public class LibraryManagementApp extends JFrame {
    private final LibraryRepository repository = new LibraryRepository();
    private final JTextField search = new JTextField(20);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Title", "Author", "Total", "Available"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public LibraryManagementApp() throws SQLException {
        super("Library Management System");
        repository.initialize();

        JPanel top = new JPanel();
        top.add(new JLabel("Title or author:"));
        top.add(search);
        addButton(top, "Search", this::refreshBooks);
        addButton(top, "Show All", () -> {
            search.setText("");
            refreshBooks();
        });
        search.addActionListener(event -> runAction(this::refreshBooks));

        JPanel bottom = new JPanel();
        addButton(bottom, "Add Book", this::addBook);
        addButton(bottom, "Issue Book", this::issueBook);
        addButton(bottom, "Return Book", this::returnBook);
        addButton(bottom, "Loan Report", this::showReport);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(850, 450);
        setLocationRelativeTo(null);
        refreshBooks();
    }

    private void refreshBooks() throws SQLException {
        var books = repository.findBooks(search.getText());
        model.setRowCount(0);

        for (Book book : books) {
            model.addRow(new Object[]{
                    book.id(), book.title(), book.author(),
                    book.totalCopies(), book.availableCopies()
            });
        }
    }

    private void addBook() throws SQLException {
        JTextField title = new JTextField();
        JTextField author = new JTextField();
        JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));

        Object[] form = {"Title:", title, "Author:", author, "Copies:", copies};
        int choice = JOptionPane.showConfirmDialog(
                this, form, "Add Book", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) return;

        if (title.getText().isBlank() || author.getText().isBlank()) {
            throw new IllegalArgumentException("Title and author are required.");
        }

        repository.addBook(title.getText().trim(), author.getText().trim(),
                (Integer) copies.getValue());
        search.setText("");
        refreshBooks();
    }

    private void issueBook() throws SQLException {
        int row = table.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select a book from the table first.");
        }

        int bookId = (Integer) table.getValueAt(row, 0);
        String borrower = JOptionPane.showInputDialog(this, "Borrower name:");
        if (borrower == null) return;

        boolean issued = repository.issueBook(bookId, borrower);
        refreshBooks();
        JOptionPane.showMessageDialog(this, issued
                ? "Book issued successfully."
                : "Book not found or no copies available.");
    }

    private void returnBook() throws SQLException {
        String input = JOptionPane.showInputDialog(
                this, "Enter the loan ID shown in Loan Report:");
        if (input == null) return;

        int loanId;
        try {
            loanId = Integer.parseInt(input.trim());
            if (loanId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a positive loan ID, for example 1.");
        }

        boolean returned = repository.returnBook(loanId);
        refreshBooks();
        JOptionPane.showMessageDialog(this, returned
                ? "Book returned successfully."
                : "Loan not found or already returned.");
    }

    private void showReport() throws SQLException {
        JTextArea report = new JTextArea(repository.getLoanReport(), 12, 75);
        report.setEditable(false);
        report.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(report),
                "Loan Report", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addButton(JPanel panel, String label, SqlAction action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> runAction(action));
        panel.add(button);
    }

    private void runAction(SqlAction action) {
        try {
            action.run();
        } catch (SQLException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new LibraryManagementApp().setVisible(true);
            } catch (SQLException exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
