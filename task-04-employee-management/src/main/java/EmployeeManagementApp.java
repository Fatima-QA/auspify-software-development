import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.sql.SQLException;
import java.util.List;

public class EmployeeManagementApp extends JFrame {
    private final EmployeeRepository repository = new EmployeeRepository();
    private final JTextField search = new JTextField(22);

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Code", "Name", "Email", "Job Title", "Department"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);
    private List<EmployeeRepository.EmployeeRow> employees = List.of();

    public EmployeeManagementApp() throws SQLException {
        super("Employee Management System");
        repository.initialize();

        JPanel filters = new JPanel();
        filters.add(new JLabel("Name, code or department:"));
        filters.add(search);
        addButton(filters, "Search", this::refresh);
        addButton(filters, "Show All", this::showAll);
        search.addActionListener(event -> runAction(this::refresh));

        JPanel actions = new JPanel();
        addButton(actions, "Add Employee", () -> editEmployee(null));
        addButton(actions, "Update", () -> editEmployee(selectedEmployee()));
        addButton(actions, "Delete", this::deleteEmployee);
        addButton(actions, "Add Department", this::addDepartment);
        addButton(actions, "Department Report", this::showReport);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(filters, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 450);
        setLocationRelativeTo(null);
        refresh();
    }

    private void refresh() throws SQLException {
        employees = repository.searchEmployees(search.getText());
        model.setRowCount(0);
        for (var employee : employees) {
            model.addRow(new Object[]{
                    employee.code(), employee.fullName(), employee.email(),
                    employee.jobTitle(), employee.departmentName()
            });
        }
    }

    private void showAll() throws SQLException {
        search.setText("");
        refresh();
    }

    private EmployeeRepository.EmployeeRow selectedEmployee() {
        int row = table.getSelectedRow();
        if (row < 0) {
            throw new IllegalArgumentException("Select an employee first.");
        }
        return employees.get(table.convertRowIndexToModel(row));
    }

    private void editEmployee(EmployeeRepository.EmployeeRow employee)
            throws SQLException {
        boolean editing = employee != null;

        JTextField code = new JTextField(editing ? employee.code() : "");
        JTextField name = new JTextField(editing ? employee.fullName() : "");
        JTextField email = new JTextField(editing ? employee.email() : "");
        JTextField job = new JTextField(editing ? employee.jobTitle() : "");
        JTextField department = new JTextField(
                editing ? employee.departmentName() : "");

        code.setEditable(!editing);

        Object[] form = {
                "Employee code:", code,
                "Full name:", name,
                "Email:", email,
                "Job title:", job,
                "Department (created if new):", department
        };

        int answer = JOptionPane.showConfirmDialog(
                this, form, editing ? "Update Employee" : "Add Employee",
                JOptionPane.OK_CANCEL_OPTION);

        if (answer != JOptionPane.OK_OPTION) return;

        if (code.getText().isBlank() || name.getText().isBlank()
                || email.getText().isBlank() || job.getText().isBlank()
                || department.getText().isBlank()) {
            throw new IllegalArgumentException("All fields are required.");
        }

        if (!email.getText().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        int departmentId = repository.addDepartment(department.getText());

        if (editing) {
            if (!repository.updateEmployee(code.getText(), name.getText(),
                    email.getText(), job.getText(), departmentId)) {
                throw new IllegalArgumentException("Employee was not found.");
            }
        } else {
            repository.addEmployee(code.getText(), name.getText(),
                    email.getText(), job.getText(), departmentId);
        }

        showAll();
    }

    private void deleteEmployee() throws SQLException {
        var employee = selectedEmployee();
        int answer = JOptionPane.showConfirmDialog(
                this, "Delete " + employee.fullName() + "?",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            if (!repository.deleteEmployee(employee.code())) {
                throw new IllegalArgumentException("Employee was not found.");
            }
            refresh();
        }
    }

    private void addDepartment() throws SQLException {
        String name = JOptionPane.showInputDialog(this, "Department name:");
        if (name == null) return;

        repository.addDepartment(name);
        JOptionPane.showMessageDialog(this, "Department is available: " + name.trim());
    }

    private void showReport() throws SQLException {
        JOptionPane.showMessageDialog(this, repository.getDepartmentReport(),
                "Department Report", JOptionPane.INFORMATION_MESSAGE);
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
                new EmployeeManagementApp().setVisible(true);
            } catch (SQLException exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}