# Software Requirements Specification (SRS)
## Waste Collection Management and Environmental Data Analysis System

## 1. Introduction

### 1.1 Purpose
To specify the requirements of a system that helps a municipal administration manage waste collection, track vehicles, and analyze waste generation patterns.

### 1.2 Scope
The system maintains area and vehicle details, records waste collection activities, assigns collections to vehicles, tracks status, and generates reports and visualizations. It is developed using Java (application), SQL database (storage), and Python (analysis).

### 1.3 Definitions
| Term | Meaning |
|---|---|
| SRS | Software Requirements Specification |
| JDBC | Java Database Connectivity |
| Biodegradable | Waste that decomposes naturally |
| Recyclable | Waste that can be reprocessed |
| Non-recyclable | Waste that cannot be reprocessed |

## 2. Overall Description

### 2.1 Product Perspective
A standalone system with three layers: Java application, relational database, and Python analytics module.

### 2.2 Users
| User | Role |
|---|---|
| Admin | Manages areas, vehicles, assignments, reports |
| Collection Staff | Records collections and updates status |

### 2.3 Assumptions
- Each collection belongs to one area and one vehicle.
- Quantities are recorded in kilograms.

## 3. Functional Requirements

| ID | Requirement |
|---|---|
| FR1 | Add, update, view residential areas (area ID, name, location, population) |
| FR2 | Add, update, view vehicles (vehicle ID, type, capacity) |
| FR3 | Record collection activity (date, area, waste type, quantity) |
| FR4 | Assign a collection to a vehicle |
| FR5 | Track status: Pending, In Progress, Completed |
| FR6 | Record quantity by category: biodegradable, recyclable, non-recyclable |
| FR7 | Report: area-wise waste collection |
| FR8 | Report: vehicle utilization |
| FR9 | Report: collection completion |
| FR10 | Python retrieves data from DB, preprocesses it, and generates charts |

## 4. Non-Functional Requirements

| Type | Requirement |
|---|---|
| Performance | Reports generated within 3 seconds |
| Usability | Simple menu-driven interface |
| Reliability | Data stored safely in the database |
| Security | Only authorized users access the system |
| Maintainability | Modular, object-oriented code |

## 5. System Requirements

| Item | Requirement |
|---|---|
| Hardware | 4 GB RAM, dual-core processor |
| Software | JDK 17+, MySQL/SQLite, Python 3.x |
| Python libraries | pandas, matplotlib, seaborn |

## 6. Constraints
- Vehicle capacity must not be exceeded by assigned quantity.
- Quantity must be a positive number.
