import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class StudentRepository {
    private final Path filePath = Path.of("data", "students.dat");

    public void save(List<Student> students) throws IOException {
        Files.createDirectories(filePath.getParent());

        Path tempPath = filePath.resolveSibling("students.tmp");

        try (DataOutputStream output = new DataOutputStream(
                Files.newOutputStream(tempPath))) {

            output.writeInt(students.size());

            for (Student student : students) {
                writeText(output, student.getId());
                writeText(output, student.getFirstName());
                writeText(output, student.getLastName());
                writeText(output, student.getEmail());
                writeText(output, student.getGroup());
            }
        }

        Files.move(tempPath, filePath,
                StandardCopyOption.REPLACE_EXISTING);
    }

    public List<Student> load() throws IOException {
        List<Student> students = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return students;
        }

        try (DataInputStream input = new DataInputStream(
                Files.newInputStream(filePath))) {

            int count = input.readInt();

            if (count < 0 || count > 100_000) {
                throw new IOException("Invalid student count in data file.");
            }

            for (int i = 0; i < count; i++) {
                Student student = new Student(
                        readText(input),
                        readText(input),
                        readText(input),
                        readText(input),
                        readText(input)
                );

                students.add(student);
            }
        }

        return students;
    }

    private void writeText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private String readText(DataInputStream input) throws IOException {
        int length = input.readInt();

        if (length < 0 || length > 1_000_000) {
            throw new IOException("Invalid text length in data file.");
        }

        byte[] bytes = new byte[length];
        input.readFully(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }
}