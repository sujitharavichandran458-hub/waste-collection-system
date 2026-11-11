import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// ======================= MODEL CLASSES =======================

class Area {
    private int areaId;
    private String areaName;
    private String location;
    private int population;

    Area(int areaId, String areaName, String location, int population) {
        this.areaId = areaId;
        this.areaName = areaName;
        this.location = location;
        this.population = population;
    }

    String getAreaName() { return areaName; }
    String getLocation() { return location; }
    int getPopulation() { return population; }

    @Override
    public String toString() {
        return String.format("%-4d %-18s %-14s %10d", areaId, areaName, location, population);
    }
}

class Vehicle {
    private int vehicleId;
    private String vehicleType;
    private double capacity;

    Vehicle(int vehicleId, String vehicleType, double capacity) {
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.capacity = capacity;
    }

    String getVehicleType() { return vehicleType; }
    double getCapacity() { return capacity; }

    @Override
    public String toString() {
        return String.format("%-4d %-14s %10.0f", vehicleId, vehicleType, capacity);
    }
}

class CollectionRecord {
    private int collectionId;
    private String collectionDate;
    private int areaId;
    private int vehicleId;
    private String status;

    CollectionRecord(int collectionId, String collectionDate, int areaId, int vehicleId, String status) {
        this.collectionId = collectionId;
        this.collectionDate = collectionDate;
        this.areaId = areaId;
        this.vehicleId = vehicleId;
        this.status = status;
    }

    String getCollectionDate() { return collectionDate; }
    int getAreaId() { return areaId; }
    int getVehicleId() { return vehicleId; }
    String getStatus() { return status; }
}

class WasteDetail {
    private int detailId;
    private int collectionId;
    private String wasteType;
    private double quantity;

    WasteDetail(int detailId, int collectionId, String wasteType, double quantity) {
        this.detailId = detailId;
        this.collectionId = collectionId;
        this.wasteType = wasteType;
        this.quantity = quantity;
    }

    int getCollectionId() { return collectionId; }
    String getWasteType() { return wasteType; }
    double getQuantity() { return quantity; }
}

// ======================= DATABASE CONNECTION =======================

class DBConnection {
    private static final String URL = "jdbc:sqlite:waste_collection.db";

    static Connection getConnection() throws SQLException {
        Connection con = DriverManager.getConnection(URL);
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return con;
    }

    // Runs the schema + sample data part of the SQL script (stops before report queries)
    static void initialize(String sqlPath) throws SQLException, IOException {
        String script = new String(Files.readAllBytes(Paths.get(sqlPath)));
        int cut = script.indexOf("-- ---------- 3. REPORT QUERIES");
        if (cut > 0) {
            script = script.substring(0, cut);
        }
        StringBuilder clean = new StringBuilder();
        for (String line : script.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            clean.append(line).append("\n");
        }
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            for (String sql : clean.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    st.execute(sql.trim());
                }
            }
        }
    }
}

// ======================= BUSINESS LOGIC =======================

class WasteService {
    static final String[] STATUSES = {"Pending", "In Progress", "Completed"};
    static final String[] WASTE_TYPES = {"Biodegradable", "Recyclable", "Non-Recyclable"};

    private boolean exists(Connection con, String table, String column, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int lastInsertId(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private double currentLoad(Connection con, int collectionId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM Waste_Detail WHERE collection_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, collectionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private double vehicleCapacity(Connection con, int vehicleId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT capacity FROM Vehicle WHERE vehicle_id = ?")) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Vehicle ID " + vehicleId + " not found.");
                }
                return rs.getDouble(1);
            }
        }
    }

    int addArea(Area a) throws SQLException {
        String sql = "INSERT INTO Area(area_name, location, population) VALUES (?, ?, ?)";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, a.getAreaName());
            ps.setString(2, a.getLocation());
            ps.setInt(3, a.getPopulation());
            ps.executeUpdate();
            return lastInsertId(con);
        }
    }

    int addVehicle(Vehicle v) throws SQLException {
        String sql = "INSERT INTO Vehicle(vehicle_type, capacity) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, v.getVehicleType());
            ps.setDouble(2, v.getCapacity());
            ps.executeUpdate();
            return lastInsertId(con);
        }
    }

    int recordCollection(CollectionRecord c) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            if (!exists(con, "Area", "area_id", c.getAreaId())) {
                throw new IllegalArgumentException("Area ID " + c.getAreaId() + " not found.");
            }
            if (!exists(con, "Vehicle", "vehicle_id", c.getVehicleId())) {
                throw new IllegalArgumentException("Vehicle ID " + c.getVehicleId() + " not found.");
            }
            String sql = "INSERT INTO Collection(collection_date, area_id, vehicle_id, status) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, c.getCollectionDate());
                ps.setInt(2, c.getAreaId());
                ps.setInt(3, c.getVehicleId());
                ps.setString(4, c.getStatus());
                ps.executeUpdate();
            }
            return lastInsertId(con);
        }
    }

    int addWasteDetail(WasteDetail w) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT v.capacity FROM Collection c JOIN Vehicle v ON c.vehicle_id = v.vehicle_id "
                       + "WHERE c.collection_id = ?";
            double capacity;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, w.getCollectionId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Collection ID " + w.getCollectionId() + " not found.");
                    }
                    capacity = rs.getDouble(1);
                }
            }
            double load = currentLoad(con, w.getCollectionId());
            if (load + w.getQuantity() > capacity) {
                throw new IllegalArgumentException(String.format(
                    "Capacity exceeded. Vehicle capacity %.1f kg, current load %.1f kg.", capacity, load));
            }
            String ins = "INSERT INTO Waste_Detail(collection_id, waste_type, quantity) VALUES (?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(ins)) {
                ps.setInt(1, w.getCollectionId());
                ps.setString(2, w.getWasteType());
                ps.setDouble(3, w.getQuantity());
                ps.executeUpdate();
            }
            return lastInsertId(con);
        }
    }

    void assignVehicle(int collectionId, int vehicleId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            if (!exists(con, "Collection", "collection_id", collectionId)) {
                throw new IllegalArgumentException("Collection ID " + collectionId + " not found.");
            }
            double capacity = vehicleCapacity(con, vehicleId);
            double load = currentLoad(con, collectionId);
            if (load > capacity) {
                throw new IllegalArgumentException(String.format(
                    "Collected waste (%.1f kg) exceeds this vehicle's capacity (%.1f kg).", load, capacity));
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE Collection SET vehicle_id = ? WHERE collection_id = ?")) {
                ps.setInt(1, vehicleId);
                ps.setInt(2, collectionId);
                ps.executeUpdate();
            }
        }
    }

    void updateStatus(int collectionId, String status) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE Collection SET status = ? WHERE collection_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, collectionId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Collection ID " + collectionId + " not found.");
            }
        }
    }

    List<Area> getAreas() throws SQLException {
        List<Area> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM Area ORDER BY area_id")) {
            while (rs.next()) {
                list.add(new Area(rs.getInt("area_id"), rs.getString("area_name"),
                                  rs.getString("location"), rs.getInt("population")));
            }
        }
        return list;
    }

    List<Vehicle> getVehicles() throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM Vehicle ORDER BY vehicle_id")) {
            while (rs.next()) {
                list.add(new Vehicle(rs.getInt("vehicle_id"), rs.getString("vehicle_type"),
                                     rs.getDouble("capacity")));
            }
        }
        return list;
    }

    void printCollections() throws SQLException {
        String sql = "SELECT c.collection_id, c.collection_date, a.area_name, v.vehicle_type, c.status, "
                   + "COALESCE(SUM(w.quantity), 0) AS total "
                   + "FROM Collection c JOIN Area a ON c.area_id = a.area_id "
                   + "JOIN Vehicle v ON c.vehicle_id = v.vehicle_id "
                   + "LEFT JOIN Waste_Detail w ON c.collection_id = w.collection_id "
                   + "GROUP BY c.collection_id ORDER BY c.collection_id";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.printf("%-4s %-11s %-18s %-12s %-12s %10s%n",
                              "ID", "Date", "Area", "Vehicle", "Status", "Total (kg)");
            while (rs.next()) {
                System.out.printf("%-4d %-11s %-18s %-12s %-12s %10.1f%n",
                    rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getDouble(6));
            }
        }
    }
}

// ======================= REPORTS =======================

class ReportService {

    void areaWiseReport() throws SQLException {
        String sql = "SELECT a.area_id, a.area_name, COUNT(DISTINCT c.collection_id) AS collections, "
                   + "COALESCE(SUM(w.quantity), 0) AS total_kg "
                   + "FROM Area a LEFT JOIN Collection c ON a.area_id = c.area_id "
                   + "LEFT JOIN Waste_Detail w ON c.collection_id = w.collection_id "
                   + "GROUP BY a.area_id, a.area_name ORDER BY total_kg DESC";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- AREA-WISE WASTE COLLECTION REPORT ---");
            System.out.printf("%-4s %-18s %-12s %10s%n", "ID", "Area", "Collections", "Total (kg)");
            while (rs.next()) {
                System.out.printf("%-4d %-18s %-12d %10.1f%n",
                    rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getDouble(4));
            }
        }
    }

    void vehicleUtilizationReport() throws SQLException {
        String sql = "SELECT v.vehicle_id, v.vehicle_type, v.capacity, COUNT(c.collection_id) AS trips, "
                   + "COALESCE(SUM(t.total), 0) AS total_kg, "
                   + "COALESCE(ROUND(AVG(t.total) * 100.0 / v.capacity, 1), 0) AS avg_load_pct "
                   + "FROM Vehicle v LEFT JOIN Collection c ON v.vehicle_id = c.vehicle_id "
                   + "LEFT JOIN (SELECT collection_id, SUM(quantity) AS total FROM Waste_Detail "
                   + "GROUP BY collection_id) t ON t.collection_id = c.collection_id "
                   + "GROUP BY v.vehicle_id, v.vehicle_type, v.capacity ORDER BY v.vehicle_id";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- VEHICLE UTILIZATION REPORT ---");
            System.out.printf("%-4s %-12s %9s %6s %12s %10s%n",
                              "ID", "Type", "Cap(kg)", "Trips", "Total(kg)", "Avg Load%");
            while (rs.next()) {
                System.out.printf("%-4d %-12s %9.0f %6d %12.1f %9.1f%%%n",
                    rs.getInt(1), rs.getString(2), rs.getDouble(3),
                    rs.getInt(4), rs.getDouble(5), rs.getDouble(6));
            }
        }
    }

    void completionReport() throws SQLException {
        String sql = "SELECT status, COUNT(*) AS cnt, "
                   + "COUNT(*) * 100.0 / (SELECT COUNT(*) FROM Collection) AS pct "
                   + "FROM Collection GROUP BY status ORDER BY cnt DESC";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- COLLECTION COMPLETION REPORT ---");
            System.out.printf("%-12s %6s %12s%n", "Status", "Count", "Percentage");
            while (rs.next()) {
                System.out.printf("%-12s %6d %11.1f%%%n", rs.getString(1), rs.getInt(2), rs.getDouble(3));
            }
        }
    }
}

// ======================= MAIN (MENU) =======================

public class WasteCollectionApp {
    private static final Scanner sc = new Scanner(System.in);
    private static final String SQL_FILE = "sql/waste_collection.sql";

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("SQLite JDBC jar not found. Add it to the classpath (see README).");
            return;
        }
        ensureDatabase();

        WasteService service = new WasteService();
        ReportService reports = new ReportService();

        while (true) {
            printMenu();
            int choice = readInt("Enter choice: ");
            try {
                switch (choice) {
                    case 1: addArea(service); break;
                    case 2: addVehicle(service); break;
                    case 3: recordCollection(service); break;
                    case 4: addWasteDetail(service); break;
                    case 5: assignVehicle(service); break;
                    case 6: updateStatus(service); break;
                    case 7:
                        System.out.printf("%-4s %-18s %-14s %10s%n", "ID", "Area", "Location", "Population");
                        for (Area a : service.getAreas()) System.out.println(a);
                        break;
                    case 8:
                        System.out.printf("%-4s %-14s %10s%n", "ID", "Type", "Cap (kg)");
                        for (Vehicle v : service.getVehicles()) System.out.println(v);
                        break;
                    case 9: service.printCollections(); break;
                    case 10: reports.areaWiseReport(); break;
                    case 11: reports.vehicleUtilizationReport(); break;
                    case 12: reports.completionReport(); break;
                    case 13:
                        DBConnection.initialize(SQL_FILE);
                        System.out.println("Database reset with sample data.");
                        break;
                    case 0:
                        System.out.println("Thank you. Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            } catch (SQLException | IOException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void ensureDatabase() {
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            st.executeQuery("SELECT COUNT(*) FROM Area").close();
        } catch (SQLException e) {
            System.out.println("Creating database from " + SQL_FILE + " ...");
            try {
                DBConnection.initialize(SQL_FILE);
                System.out.println("Database ready.");
            } catch (SQLException | IOException ex) {
                System.out.println("Could not create database: " + ex.getMessage());
                System.out.println("Run the program from the project root folder.");
                System.exit(1);
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n===== WASTE COLLECTION MANAGEMENT SYSTEM =====");
        System.out.println(" 1. Add Area");
        System.out.println(" 2. Add Vehicle");
        System.out.println(" 3. Record Collection");
        System.out.println(" 4. Add Waste Detail");
        System.out.println(" 5. Assign Vehicle to Collection");
        System.out.println(" 6. Update Collection Status");
        System.out.println(" 7. View Areas");
        System.out.println(" 8. View Vehicles");
        System.out.println(" 9. View Collections");
        System.out.println("10. Report: Area-wise Waste");
        System.out.println("11. Report: Vehicle Utilization");
        System.out.println("12. Report: Collection Completion");
        System.out.println("13. Reset Database (reload SQL script)");
        System.out.println(" 0. Exit");
    }

    // ---------- menu actions ----------

    private static void addArea(WasteService s) throws SQLException {
        String name = readText("Area name: ");
        String loc = readText("Location: ");
        int pop = readPositiveInt("Population: ");
        int id = s.addArea(new Area(0, name, loc, pop));
        System.out.println("Area added with ID " + id);
    }

    private static void addVehicle(WasteService s) throws SQLException {
        String type = readText("Vehicle type: ");
        double cap = readPositiveDouble("Capacity (kg): ");
        int id = s.addVehicle(new Vehicle(0, type, cap));
        System.out.println("Vehicle added with ID " + id);
    }

    private static void recordCollection(WasteService s) throws SQLException {
        String date = readDate("Collection date (YYYY-MM-DD): ");
        int areaId = readInt("Area ID: ");
        int vehicleId = readInt("Vehicle ID: ");
        int id = s.recordCollection(new CollectionRecord(0, date, areaId, vehicleId, "Pending"));
        System.out.println("Collection recorded with ID " + id + " (status: Pending)");
    }

    private static void addWasteDetail(WasteService s) throws SQLException {
        int cid = readInt("Collection ID: ");
        String type = readWasteType();
        double qty = readPositiveDouble("Quantity (kg): ");
        s.addWasteDetail(new WasteDetail(0, cid, type, qty));
        System.out.println("Waste detail added.");
    }

    private static void assignVehicle(WasteService s) throws SQLException {
        int cid = readInt("Collection ID: ");
        int vid = readInt("New Vehicle ID: ");
        s.assignVehicle(cid, vid);
        System.out.println("Vehicle assigned.");
    }

    private static void updateStatus(WasteService s) throws SQLException {
        int cid = readInt("Collection ID: ");
        String status = readStatus();
        s.updateStatus(cid, status);
        System.out.println("Status updated to " + status);
    }

    // ---------- input helpers (validation) ----------

    private static int readInt(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static int readPositiveInt(String msg) {
        while (true) {
            int n = readInt(msg);
            if (n > 0) return n;
            System.out.println("Value must be greater than 0.");
        }
    }

    private static double readPositiveDouble(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                double d = Double.parseDouble(sc.nextLine().trim());
                if (d > 0) return d;
                System.out.println("Value must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static String readText(String msg) {
        while (true) {
            System.out.print(msg);
            String t = sc.nextLine().trim();
            if (!t.isEmpty()) return t;
            System.out.println("Input cannot be empty.");
        }
    }

    private static String readDate(String msg) {
        while (true) {
            System.out.print(msg);
            String t = sc.nextLine().trim();
            try {
                return LocalDate.parse(t).toString();
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use YYYY-MM-DD.");
            }
        }
    }

    private static String readStatus() {
        while (true) {
            System.out.println("Status: 1. Pending  2. In Progress  3. Completed");
            int n = readInt("Choose: ");
            if (n >= 1 && n <= 3) return WasteService.STATUSES[n - 1];
            System.out.println("Choose 1 to 3.");
        }
    }

    private static String readWasteType() {
        while (true) {
            System.out.println("Waste type: 1. Biodegradable  2. Recyclable  3. Non-Recyclable");
            int n = readInt("Choose: ");
            if (n >= 1 && n <= 3) return WasteService.WASTE_TYPES[n - 1];
            System.out.println("Choose 1 to 3.");
        }
    }
}
