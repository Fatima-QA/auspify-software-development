import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class LibraryRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:library.db";

    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL,
                    total_copies INTEGER NOT NULL CHECK (total_copies > 0),
                    available_copies INTEGER NOT NULL
                        CHECK (available_copies BETWEEN 0 AND total_copies)
                )
                """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            statement.executeUpdate("""
        CREATE TABLE IF NOT EXISTS loans (
            id INTEGER PRIMARY KEY,
            book_id INTEGER NOT NULL REFERENCES books(id),
            borrower TEXT NOT NULL,
            issued_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            returned_at TEXT
        )
        """);
        }
    }

    public void addBook(String title, String author, int copies) throws SQLException {
        String sql = """
            INSERT INTO books (title, author, total_copies, available_copies)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, author);
            statement.setInt(3, copies);
            statement.setInt(4, copies);
            statement.executeUpdate();
        }
    }

    public void printBooks() throws SQLException {
        String sql = "SELECT title, author, available_copies, total_copies FROM books ORDER BY id";

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                System.out.printf("%s | %s | Available: %d/%d%n",
                        rows.getString("title"),
                        rows.getString("author"),
                        rows.getInt("available_copies"),
                        rows.getInt("total_copies"));
            }
        }
    }


    public void searchBooks(String keyword) throws SQLException {
        String sql = """
            SELECT title, author, available_copies, total_copies
            FROM books
            WHERE instr(lower(title), lower(?)) > 0
               OR instr(lower(author), lower(?)) > 0
            ORDER BY title
            """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword.trim());
            statement.setString(2, keyword.trim());

            try (var rows = statement.executeQuery()) {
                boolean found = false;

                while (rows.next()) {
                    found = true;
                    System.out.printf("%s | %s | Available: %d/%d%n",
                            rows.getString("title"),
                            rows.getString("author"),
                            rows.getInt("available_copies"),
                            rows.getInt("total_copies"));
                }

                if (!found) {
                    System.out.println("No matching books found.");
                }
            }
        }
    }

    public boolean issueBook(int bookId, String borrower) throws SQLException {
        if (borrower == null || borrower.isBlank()) {
            throw new IllegalArgumentException("Borrower name is required.");
        }

        String updateSql = """
            UPDATE books
            SET available_copies = available_copies - 1
            WHERE id = ? AND available_copies > 0
            """;
        String loanSql = "INSERT INTO loans (book_id, borrower) VALUES (?, ?)";

        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            connection.setAutoCommit(false);

            try (PreparedStatement update = connection.prepareStatement(updateSql);
                 PreparedStatement loan = connection.prepareStatement(loanSql)) {

                update.setInt(1, bookId);
                if (update.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }

                loan.setInt(1, bookId);
                loan.setString(2, borrower.trim());
                loan.executeUpdate();

                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean returnBook(int loanId) throws SQLException {
        String returnSql = """
            UPDATE loans
            SET returned_at = CURRENT_TIMESTAMP
            WHERE id = ? AND returned_at IS NULL
            """;

        String restoreCopySql = """
            UPDATE books
            SET available_copies = available_copies + 1
            WHERE id = (SELECT book_id FROM loans WHERE id = ?)
            """;

        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            connection.setAutoCommit(false);

            try (PreparedStatement markReturned = connection.prepareStatement(returnSql);
                 PreparedStatement restoreCopy = connection.prepareStatement(restoreCopySql)) {

                markReturned.setInt(1, loanId);
                if (markReturned.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }

                restoreCopy.setInt(1, loanId);
                if (restoreCopy.executeUpdate() != 1) {
                    throw new SQLException("The book linked to this loan was not found.");
                }

                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }


    public String getLoanReport() throws SQLException {
        String sql = """
            SELECT l.id AS loan_id, b.title, l.borrower,
                   l.issued_at, l.returned_at
            FROM loans l
            JOIN books b ON b.id = l.book_id
            ORDER BY l.id DESC
            """;

        StringBuilder report = new StringBuilder("=== Loan Report ===\n");

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {

            while (rows.next()) {
                String returnedAt = rows.getString("returned_at");

                report.append(String.format(
                        "#%d | %s | %s | Issued: %s | Returned: %s | %s%n",
                        rows.getInt("loan_id"),
                        rows.getString("title"),
                        rows.getString("borrower"),
                        rows.getString("issued_at"),
                        returnedAt == null ? "-" : returnedAt,
                        returnedAt == null ? "ON LOAN" : "RETURNED"));
            }
        }

        return report.toString();
    }

    public java.util.List<Book> findBooks(String keyword) throws SQLException {
        String sql = """
            SELECT id, title, author, total_copies, available_copies
            FROM books
            WHERE instr(lower(title), lower(?)) > 0
               OR instr(lower(author), lower(?)) > 0
            ORDER BY title, id
            """;

        var books = new java.util.ArrayList<Book>();

        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword.trim());
            statement.setString(2, keyword.trim());

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    books.add(new Book(
                            rows.getInt("id"),
                            rows.getString("title"),
                            rows.getString("author"),
                            rows.getInt("total_copies"),
                            rows.getInt("available_copies")));
                }
            }
        }
        return books;
    }


    public static void main(String[] args) throws SQLException {
        LibraryRepository repository = new LibraryRepository();
        repository.initialize();
        System.out.print(repository.getLoanReport());    }
    }