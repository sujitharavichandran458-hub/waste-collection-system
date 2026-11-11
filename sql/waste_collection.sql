-- =====================================================
-- Waste Collection Management System (SQLite)
-- =====================================================

PRAGMA foreign_keys = ON;

-- ---------- 1. SCHEMA ----------
DROP TABLE IF EXISTS Waste_Detail;
DROP TABLE IF EXISTS Collection;
DROP TABLE IF EXISTS Vehicle;
DROP TABLE IF EXISTS Area;

CREATE TABLE Area (
    area_id     INTEGER PRIMARY KEY,
    area_name   TEXT NOT NULL,
    location    TEXT NOT NULL,
    population  INTEGER NOT NULL CHECK (population > 0)
);

CREATE TABLE Vehicle (
    vehicle_id    INTEGER PRIMARY KEY,
    vehicle_type  TEXT NOT NULL,
    capacity      REAL NOT NULL CHECK (capacity > 0)
);

CREATE TABLE Collection (
    collection_id    INTEGER PRIMARY KEY,
    collection_date  TEXT NOT NULL,
    area_id          INTEGER NOT NULL,
    vehicle_id       INTEGER NOT NULL,
    status           TEXT NOT NULL DEFAULT 'Pending'
                     CHECK (status IN ('Pending', 'In Progress', 'Completed')),
    FOREIGN KEY (area_id) REFERENCES Area(area_id),
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
);

CREATE TABLE Waste_Detail (
    detail_id      INTEGER PRIMARY KEY,
    collection_id  INTEGER NOT NULL,
    waste_type     TEXT NOT NULL
                   CHECK (waste_type IN ('Biodegradable', 'Recyclable', 'Non-Recyclable')),
    quantity       REAL NOT NULL CHECK (quantity > 0),
    FOREIGN KEY (collection_id) REFERENCES Collection(collection_id)
);

-- ---------- 2. SAMPLE DATA ----------
INSERT INTO Area VALUES
(1, 'Surampatti',      'Erode East',  32000),
(2, 'Bhavani Road',    'Erode North', 41000),
(3, 'Perundurai Road', 'Erode West',  28000),
(4, 'Kasipalayam',     'Erode South', 18000),
(5, 'Moolapalayam',    'Erode East',  36000),
(6, 'Veerappanchatram','Erode Central',25000);

INSERT INTO Vehicle VALUES
(1, 'Compactor',   5000),
(2, 'Mini Truck',  2000),
(3, 'Tipper',      3500),
(4, 'Auto Tipper', 1000);

INSERT INTO Collection VALUES
(1,  '2026-08-03', 1, 1, 'Completed'),
(2,  '2026-08-10', 2, 2, 'Completed'),
(3,  '2026-08-17', 3, 3, 'Completed'),
(4,  '2026-08-24', 4, 4, 'Completed'),
(5,  '2026-09-02', 5, 1, 'Completed'),
(6,  '2026-09-09', 6, 2, 'Completed'),
(7,  '2026-09-16', 1, 3, 'Completed'),
(8,  '2026-09-23', 2, 4, 'Completed'),
(9,  '2026-10-01', 3, 1, 'Completed'),
(10, '2026-10-08', 5, 2, 'Completed'),
(11, '2026-10-15', 1, 3, 'In Progress'),
(12, '2026-10-22', 6, 1, 'Pending');

INSERT INTO Waste_Detail (collection_id, waste_type, quantity) VALUES
(1,  'Biodegradable', 1800), (1,  'Recyclable', 700), (1,  'Non-Recyclable', 450),
(2,  'Biodegradable',  900), (2,  'Recyclable', 350), (2,  'Non-Recyclable', 200),
(3,  'Biodegradable', 1500), (3,  'Recyclable', 600), (3,  'Non-Recyclable', 400),
(4,  'Biodegradable',  450), (4,  'Recyclable', 180), (4,  'Non-Recyclable', 120),
(5,  'Biodegradable', 2100), (5,  'Recyclable', 800), (5,  'Non-Recyclable', 520),
(6,  'Biodegradable', 1100), (6,  'Recyclable', 450), (6,  'Non-Recyclable', 280),
(7,  'Biodegradable', 1600), (7,  'Recyclable', 650), (7,  'Non-Recyclable', 420),
(8,  'Biodegradable',  500), (8,  'Recyclable', 200), (8,  'Non-Recyclable', 130),
(9,  'Biodegradable', 2300), (9,  'Recyclable', 900), (9,  'Non-Recyclable', 600),
(10, 'Biodegradable',  950), (10, 'Recyclable', 400), (10, 'Non-Recyclable', 250),
(11, 'Biodegradable', 1700), (11, 'Recyclable', 700), (11, 'Non-Recyclable', 450),
(12, 'Biodegradable', 1900), (12, 'Recyclable', 750), (12, 'Non-Recyclable', 500);

-- ---------- 3. REPORT QUERIES ----------

-- Q1: Area-wise total waste collected
SELECT a.area_name, COUNT(DISTINCT c.collection_id) AS collections,
       SUM(w.quantity) AS total_kg
FROM Area a
JOIN Collection c   ON a.area_id = c.area_id
JOIN Waste_Detail w ON c.collection_id = w.collection_id
GROUP BY a.area_id, a.area_name
ORDER BY total_kg DESC;

-- Q2: Vehicle utilization (trips and average load vs capacity)
SELECT v.vehicle_id, v.vehicle_type, v.capacity,
       COUNT(c.collection_id) AS trips,
       ROUND(SUM(t.total), 1) AS total_kg,
       ROUND(AVG(t.total) * 100.0 / v.capacity, 1) AS avg_load_pct
FROM Vehicle v
JOIN Collection c ON v.vehicle_id = c.vehicle_id
JOIN (SELECT collection_id, SUM(quantity) AS total
      FROM Waste_Detail GROUP BY collection_id) t
     ON t.collection_id = c.collection_id
GROUP BY v.vehicle_id, v.vehicle_type, v.capacity;

-- Q3: Collection completion status
SELECT status, COUNT(*) AS total,
       ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM Collection), 1) AS percentage
FROM Collection
GROUP BY status;

-- Q4: Waste by category
SELECT waste_type, SUM(quantity) AS total_kg
FROM Waste_Detail
GROUP BY waste_type;

-- Q5: Monthly waste trend
SELECT strftime('%Y-%m', c.collection_date) AS month,
       SUM(w.quantity) AS total_kg
FROM Collection c
JOIN Waste_Detail w ON c.collection_id = w.collection_id
GROUP BY month
ORDER BY month;

-- Q6: Waste per person (kg per resident) by area
SELECT a.area_name,
       ROUND(SUM(w.quantity) * 1.0 / a.population, 3) AS kg_per_person
FROM Area a
JOIN Collection c   ON a.area_id = c.area_id
JOIN Waste_Detail w ON c.collection_id = w.collection_id
GROUP BY a.area_id, a.area_name
ORDER BY kg_per_person DESC;
