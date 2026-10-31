import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StudentManagementApp extends JFrame {
    private final StudentRepository repository = new StudentRepository();
    private final List<Student> students = new ArrayList<>();

    private final JTextField idField = new JTextField();
    private final JTextField firstNameField = new JTextField();
    private final JTextField lastNameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextField groupField = new JTextField();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "First Name", "Last Name", "Email", "Group"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);

    public StudentManagementApp() {
        setTitle("Student Management System");
        setSize(850, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridLayout(5, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        form.add(new JLabel("Student ID:"));
        form.add(idField);
        form.add(new JLabel("First Name:"));
        form.add(firstNameField);
        form.add(new JLabel("Last Name:"));
        form.add(lastNameField);
        form.add(new JLabel("Email:"));
        form.add(emailField);
        form.add(new JLabel("Group:"));
        form.add(groupField);

        JButton addButton = new JButton("Add");
        JButton updateButton = new JButton("Update");
        JButton deleteButton = new JButton("Delete");
        JButton clearButton = new JButton("Clear");

        JPanel buttons = new JPanel();
        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.add(clearButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(form, BorderLayout.CENTER);
        topPanel.add(buttons, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedStudent();
            }
        });

        addButton.addActionListener(event -> addStudent());
        updateButton.addActionListener(event -> updateStudent());
        deleteButton.addActionListener(event -> deleteStudent());
        clearButton.addActionListener(event -> clearForm());

        try {
            students.addAll(repository.load());
            refreshTable();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Student data could not be loaded.\n"
                            + exception.getMessage()
                            + "\nThe application will close to protect existing data.",
                    "Loading Error",
                    JOptionPane.ERROR_MESSAGE
            );
            throw new IllegalStateException(exception);
        }
    }

    private boolean isFormValid() {
        if (idField.getText().isBlank()
                || firstNameField.getText().isBlank()
                || lastNameField.getText().isBlank()
                || emailField.getText().isBlank()
                || groupField.getText().isBlank()) {
            showMessage("Please fill in all fields.");
            return false;
        }

        if (!emailField.getText().trim()
                .matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            showMessage("Please enter a valid email address.");
            return false;
        }

        return true;
    }

    private Student studentFromForm() {
        return new Student(
                idField.getText().trim(),
                firstNameField.getText().trim(),
                lastNameField.getText().trim(),
                emailField.getText().trim(),
                groupField.getText().trim()
        );
    }

    private void addStudent() {
        if (!idField.isEditable()) {
            showMessage("Click Clear before adding a new student.");
            return;
        }

        if (!isFormValid()) {
            return;
        }

        Student newStudent = studentFromForm();

        boolean duplicateId = students.stream()
                .anyMatch(student -> student.getId()
                        .equalsIgnoreCase(newStudent.getId()));

        if (duplicateId) {
            showMessage("This student ID already exists.");
            return;
        }

        List<Student> updatedStudents = new ArrayList<>(students);
        updatedStudents.add(newStudent);

        if (saveChanges(updatedStudents)) {
            showMessage("Student added successfully.");
        }
    }

    private void updateStudent() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            showMessage("Please select a student to update.");
            return;
        }

        if (!isFormValid()) {
            return;
        }

        List<Student> updatedStudents = new ArrayList<>(students);
        updatedStudents.set(selectedRow, studentFromForm());

        if (saveChanges(updatedStudents)) {
            showMessage("Student updated successfully.");
        }
    }

    private void deleteStudent() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            showMessage("Please select a student to delete.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete this student?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        List<Student> updatedStudents = new ArrayList<>(students);
        updatedStudents.remove(selectedRow);

        if (saveChanges(updatedStudents)) {
            showMessage("Student deleted successfully.");
        }
    }

    private boolean saveChanges(List<Student> updatedStudents) {
        try {
            repository.save(updatedStudents);

            students.clear();
            students.addAll(updatedStudents);

            refreshTable();
            clearForm();
            return true;
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    "Changes could not be saved.\n" + exception.getMessage(),
                    "Saving Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }

    private void showSelectedStudent() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            return;
        }

        Student student = students.get(selectedRow);

        idField.setText(student.getId());
        firstNameField.setText(student.getFirstName());
        lastNameField.setText(student.getLastName());
        emailField.setText(student.getEmail());
        groupField.setText(student.getGroup());

        idField.setEditable(false);
    }

    private void refreshTable() {
        tableModel.setRowCount(0);

        for (Student student : students) {
            tableModel.addRow(new Object[]{
                    student.getId(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getEmail(),
                    student.getGroup()
            });
        }
    }

    private void clearForm() {
        table.clearSelection();

        idField.setText("");
        firstNameField.setText("");
        lastNameField.setText("");
        emailField.setText("");
        groupField.setText("");

        idField.setEditable(true);
        idField.requestFocusInWindow();
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StudentManagementApp application = new StudentManagementApp();
            application.setVisible(true);
        });
    }
}
