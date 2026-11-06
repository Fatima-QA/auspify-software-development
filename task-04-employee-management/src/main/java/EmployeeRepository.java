import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class EmployeeRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:employees.db";

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DATABASE_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    public void initialize() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS departments (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL COLLATE NOCASE UNIQUE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employees (
                        id INTEGER PRIMARY KEY,
                        employee_code TEXT NOT NULL COLLATE NOCASE UNIQUE,
                        full_name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        job_title TEXT NOT NULL,
                        department_id INTEGER NOT NULL
                            REFERENCES departments(id) ON DELETE RESTRICT
                    )
                    """);
        }
    }

    public int addDepartment(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Department name is required.");
        }

        String insertSql = """
            INSERT INTO departments (name) VALUES (?)
            ON CONFLICT(name) DO NOTHING
            """;
        String selectSql = "SELECT id FROM departments WHERE name = ?";

        try (Connection connection = openConnection();
             PreparedStatement insert = connection.prepareStatement(insertSql);
             PreparedStatement select = connection.prepareStatement(selectSql)) {
            insert.setString(1, name.trim());
            insert.executeUpdate();

            select.setString(1, name.trim());
            try (var rows = select.executeQuery()) {
                if (rows.next()) {
                    return rows.getInt("id");
                }
            }
        }
        throw new SQLException("Department ID could not be found.");
    }

    public void addEmployee(String code, String fullName, String email,
                            String jobTitle, int departmentId) throws SQLException {
        if (code.isBlank() || fullName.isBlank() || email.isBlank() || jobTitle.isBlank()) {
            throw new IllegalArgumentException("All employee fields are required.");
        }

        String sql = """
            INSERT INTO employees (employee_code, full_name, email, job_title, department_id)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code.trim());
            statement.setString(2, fullName.trim());
            statement.setString(3, email.trim());
            statement.setString(4, jobTitle.trim());
            statement.setInt(5, departmentId);
            statement.executeUpdate();
        }
    }



    public String findEmployeeDetails(String code) throws SQLException {
        String sql = """
            SELECT e.employee_code, e.full_name, e.email, e.job_title,
                   d.name AS department_name
            FROM employees e
            JOIN departments d ON e.department_id = d.id
            WHERE e.employee_code = ?
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code.trim());

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return "Employee not found.";
                }

                return String.format("%s | %s | %s | %s | Department: %s",
                        rows.getString("employee_code"),
                        rows.getString("full_name"),
                        rows.getString("email"),
                        rows.getString("job_title"),
                        rows.getString("department_name"));
            }
        }
    }


    public boolean updateEmployee(String code, String fullName, String email,
                                  String jobTitle, int departmentId) throws SQLException {
        if (code.isBlank() || fullName.isBlank() || email.isBlank() || jobTitle.isBlank()) {
            throw new IllegalArgumentException("All employee fields are required.");
        }

        String sql = """
            UPDATE employees
            SET full_name = ?, email = ?, job_title = ?, department_id = ?
            WHERE employee_code = ?
            """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName.trim());
            statement.setString(2, email.trim());
            statement.setString(3, jobTitle.trim());
            statement.setInt(4, departmentId);
            statement.setString(5, code.trim());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean deleteEmployee(String code) throws SQLException {
        String sql = "DELETE FROM employees WHERE employee_code = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code.trim());
            return statement.executeUpdate() == 1;
        }
    }



    public record EmployeeRow(
            String code, String fullName, String email,
            String jobTitle, int departmentId, String departmentName
    ) {}

    public java.util.List<EmployeeRow> searchEmployees(String keyword)
            throws SQLException {
        String sql = """
            SELECT e.employee_code, e.full_name, e.email, e.job_title,
                   e.department_id, d.name AS department_name
            FROM employees e
            JOIN departments d ON d.id = e.department_id
            WHERE instr(lower(e.full_name), lower(?)) > 0
               OR instr(lower(e.employee_code), lower(?)) > 0
               OR instr(lower(d.name), lower(?)) > 0
            ORDER BY e.full_name, e.employee_code
            """;

        var employees = new java.util.ArrayList<EmployeeRow>();
        String query = keyword.trim();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setString(3, query);

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    employees.add(new EmployeeRow(
                            rows.getString("employee_code"),
                            rows.getString("full_name"),
                            rows.getString("email"),
                            rows.getString("job_title"),
                            rows.getInt("department_id"),
                            rows.getString("department_name")
                    ));
                }
            }
        }
        return employees;
    }

    public String getDepartmentReport() throws SQLException {
        String sql = """
            SELECT d.name, COUNT(e.id) AS employee_count
            FROM departments d
            LEFT JOIN employees e ON e.department_id = d.id
            GROUP BY d.id, d.name
            ORDER BY d.name
            """;

        StringBuilder report = new StringBuilder("=== Employees by Department ===\n");

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                report.append(rows.getString("name"))
                        .append(": ")
                        .append(rows.getInt("employee_count"))
                        .append('\n');
            }
        }
        return report.toString();
    }



    public static void main(String[] args) throws SQLException {
        EmployeeRepository repository = new EmployeeRepository();
        repository.initialize();

        System.out.println("By name: " + repository.searchEmployees("Aylin"));
        System.out.println("By code: " + repository.searchEmployees("EMP-001"));
        System.out.println("By department: " + repository.searchEmployees("QA"));
        System.out.println("No match: " + repository.searchEmployees("zz123"));
        System.out.print(repository.getDepartmentReport());
    }
}
