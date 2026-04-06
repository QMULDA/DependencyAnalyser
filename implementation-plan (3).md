# Dependency Analyser – Implementation Plan

## Architecture

```
IntelliJ Plugin (Java/Swing)
  └── Maven API  →  extract dependencies
  └── H2 DB      →  local persistence (5-entity 3NF schema)
  └── HTTP POST  →  export to Supabase (PostgreSQL)

Supabase (cloud PostgreSQL)
  └── REST API   →  receives plugin exports

Next.js + Recharts (Vercel)
  └── reads Supabase  →  public dashboard
```

---

## Phase 1 – IntelliJ Plugin Skeleton

### 1.1 Project Setup

- Clone from [JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) using **"Use this template"**.
- Open in IntelliJ → when prompted, set Gradle JVM to your **JDK 17** installation.
- Key files created by the template: [[source]](https://github.com/JetBrains/intellij-platform-plugin-template)
  - `build.gradle.kts` — Gradle build config
  - `gradle.properties` — plugin metadata (name, version, platform version)
  - `src/main/resources/META-INF/plugin.xml` — plugin manifest
  - `src/main/kotlin/` — source root (add a `src/main/java/` dir if writing Java)

> **✅ Verify:** Gradle tool window (right sidebar) shows an `intellij` task group with no sync errors in the Build tool window.

---

### 1.2 `plugin.xml` + `build.gradle.kts` Configuration

**`src/main/resources/META-INF/plugin.xml`** — add:
```xml
<depends>org.jetbrains.idea.maven</depends>

<extensions defaultExtensionNs="com.intellij">
  <toolWindow id="Dependency Analyser"
              anchor="right"
              factoryClass="com.example.DependencyAnalyserWindowFactory"/>
</extensions>
```

**`build.gradle.kts`** — inside the `intellijPlatform { }` dependencies block: [[source]](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)
```kotlin
dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.idea.maven")
    }
}
```

> ⚠️ The correct Maven plugin ID is `org.jetbrains.idea.maven` — **not** `com.intellij.modules.maven` (does not exist). Confirmed in [intellij-community source](https://github.com/JetBrains/intellij-community/blob/master/plugins/gradle/java/resources/META-INF/plugin.xml) and [plugin compatibility docs](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html).

> **✅ Verify:** Re-sync Gradle. Run the Gradle task **`printBundledPlugins`** (Gradle tool window → `intellij` group) and confirm `org.jetbrains.idea.maven` appears in the console output.

---

### 1.3 Tool Window (Sidebar UI)

Create `src/main/java/com/example/DependencyAnalyserWindowFactory.java` implementing `ToolWindowFactory`. In `createToolWindowContent()`, build the panel using: [[source]](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- `JBPanel` with `BorderLayout` — outer container
- `JBButton("Scan Project")` — docked to `BorderLayout.NORTH`
- `JBTable` + `DefaultTableModel` — scrollable grid in `BorderLayout.CENTER`, columns: `Group ID | Artifact ID | Version | Risk Tier | Scope`
- `JBLabel` — status text docked to `BorderLayout.SOUTH`

> **✅ Verify:** Run **Gradle → Tasks → intellij → `runIde`**. In the sandboxed IDE that opens, the **"Dependency Analyser"** tab should appear in the right sidebar. Clicking it shows the button and empty table. If the tab is absent, check that `factoryClass` in `plugin.xml` matches the fully-qualified class name exactly.

---

### 1.4 Maven API – Reading Dependencies

In your scan logic (called on button click), use: [[source]](https://github.com/JetBrains/intellij-community/blob/master/plugins/maven/src/main/java/org/jetbrains/idea/maven/execution/MavenJUnitPatcher.java)
```java
MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
for (MavenProject mp : manager.getProjects()) {
    for (MavenArtifact dep : mp.getDependencies()) {
        dep.getGroupId();    // e.g. "org.springframework"
        dep.getArtifactId(); // e.g. "spring-core"
        dep.getVersion();    // e.g. "6.1.0"
        dep.getScope();      // e.g. "compile", "test"
    }
}
```

> ⚠️ `getDependencies()` returns **direct dependencies only** — there is no `.isTransitive()` method on `MavenArtifact`. The `is_transitive` schema column has been dropped accordingly; note this limitation in your report.

> **✅ Verify:** With `runIde` running, open a Maven project in the sandbox (a Spring Boot project has enough deps to be useful). Click "Scan Project". `System.out.println()` output appears in the **Run console of your dev IDE** (not the sandbox). Confirm groupId/artifactId/version lines appear. If `getProjects()` returns empty, ensure the Maven tool window is visible in the sandbox — it must be a Maven-imported project.

---

## Phase 2 – Local H2 Database

### 2.1 Dependencies

Add to the top-level `dependencies { }` block in `build.gradle.kts` (**not** inside `intellijPlatform { }`):
```kotlin
implementation("com.h2database:h2:2.2.224")
implementation("org.flywaydb:flyway-core:9.22.3")
```

JDBC URL: `jdbc:h2:~/dependency-analyser/localdb`

> **✅ Verify:** After Gradle sync, expand **Project tool window → External Libraries** and confirm both `com.h2database:h2` and `org.flywaydb:flyway-core` are listed. If absent, check block placement. If `ClassNotFoundException` appears at runtime in the Run console, the JARs are not being bundled — see [Gradle plugin bundling docs](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

---

### 2.2 Schema (3NF)

Create `src/main/resources/db/migration/V1__initial_schema.sql`:

| Table | Columns |
|---|---|
| `project` | `project_id` PK, `name`, `path`, `last_scanned` |
| `scan` | `scan_id` PK, `project_id` FK→`project`, `scanned_at` |
| `library` | `library_id` PK, `group_id`, `artifact_id` |
| `version` | `version_id` PK, `library_id` FK→`library`, `version_string`, `risk_tier` |
| `dependency` | `dependency_id` PK, `scan_id` FK→`scan`, `version_id` FK→`version`, `scope` |

**3NF:** Library identity is separated from versions; scan metadata is separate from its found dependencies. No non-key column depends on another non-key column.

**`risk_tier` values:** `'LOW'` (patch bump), `'MEDIUM'` (minor bump), `'HIGH'` (major bump).

> **✅ Verify:** After a scan runs, connect to the database via **View → Tool Windows → Database → + → H2**, using URL `jdbc:h2:~/dependency-analyser/localdb`, user `sa`, blank password. All 5 tables should be visible. Run `SELECT * FROM project` in the Query Console to confirm rows are present.

---

### 2.3 Flyway Migrations

In your `DatabaseService` class (registered in `plugin.xml` as an `applicationService`), call on initialisation:
```java
Flyway.configure()
    .dataSource("jdbc:h2:~/dependency-analyser/localdb", "sa", "")
    .locations("classpath:db/migration")
    .load()
    .migrate();
```

Future schema changes go in new files: `V2__add_index.sql`, `V3__add_column.sql`, etc. Flyway tracks what has been applied and only runs new scripts. [[source]](https://documentation.red-gate.com/flyway/flyway-cli-and-api/concepts/migrations)

> **✅ Verify:** After plugin startup, run this in the H2 Query Console:
> ```sql
> SELECT installed_rank, description, success FROM flyway_schema_history;
> ```
> Each migration file should appear as one row with `SUCCESS = TRUE`. A missing table means `migrate()` was never called. A `FALSE` row means the SQL in that migration file has an error.

---

## Phase 3 – Cloud Backend & Dashboard

### 3.1 Supabase Setup

1. Create project at [supabase.com](https://supabase.com) → note the **Project URL** and **`anon` key** (under **Settings → API**).
2. In **SQL Editor**, run your schema DDL (same 5 tables, PostgreSQL syntax).
3. In **Authentication → Policies**, enable RLS and add:
   - `SELECT` policy for role `anon` (public reads)
   - `INSERT` policy restricted to `service_role` (plugin writes)

> **✅ Verify:** In **SQL Editor**, run:
> ```sql
> SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
> ```
> All 5 tables should appear. Then `curl https://<project>.supabase.co/rest/v1/project -H "apikey: <anon_key>"` — expect `[]` (empty array), not a `401`.

---

### 3.2 Plugin Export (Java `HttpClient`)

On "Export" button click, POST each table's data to `https://<project>.supabase.co/rest/v1/<table_name>`. Required headers: [[source]](https://supabase.com/docs/guides/api)
```
apikey: <service_role_key>
Authorization: Bearer <service_role_key>
Content-Type: application/json
Prefer: return=minimal
```

> **✅ Verify:** Click Export in the sandbox tool window. In Supabase **Table Editor**, refresh the `project` table — rows should appear. Temporarily add `System.out.println(response.statusCode())` after the HTTP call and check the dev IDE Run console. Expected: `201`. Common errors: `400` = bad JSON, `401/403` = wrong key, `409` = duplicate primary key on re-export.

---

### 3.3 Next.js Dashboard

```bash
npx create-next-app dependency-analyser-dashboard
cd dependency-analyser-dashboard
npm install @supabase/supabase-js recharts
```

- Create `.env.local` in project root: [[source]](https://supabase.com/docs/guides/getting-started/quickstarts/nextjs)
  ```
  NEXT_PUBLIC_SUPABASE_URL=https://<project>.supabase.co
  NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon_key>
  ```
- Add these same two variables in **Vercel → Project → Settings → Environment Variables** before deploying.
- Deploy: push to GitHub, connect repo to [vercel.com](https://vercel.com) → auto-deploys on each push.

**Minimum 2 charts:**
1. **Bar chart** (`BarChart` from Recharts) — libraries with `COUNT(DISTINCT version_string) > 1` per project
2. **Pie/donut chart** (`PieChart`) — `risk_tier` distribution across all dependencies

> **✅ Verify (local):** `npm run dev` → `http://localhost:3000`. Both charts should render with real data. Check browser **DevTools → Console** for Supabase errors (usually a missing/wrong env var).
>
> **✅ Verify (production):** Open the Vercel URL in an **incognito window**. Both charts must load with no login. If empty in production but working locally, the Vercel environment variables are missing or misnamed.

---

## Phase 4 – SQL Queries

### Query 1 – Version Conflict Finder
```sql
SELECT l.group_id, l.artifact_id, COUNT(DISTINCT v.version_string) AS version_count
FROM library l
JOIN version v ON v.library_id = l.library_id
JOIN dependency d ON d.version_id = v.version_id
JOIN scan s ON s.scan_id = d.scan_id
GROUP BY l.library_id, l.group_id, l.artifact_id
HAVING COUNT(DISTINCT v.version_string) > 1;
```
> **✅ Verify:** Should return at least one row if the same library appears under two different `version_string` values. If empty, run `SELECT group_id, artifact_id, version_string FROM library JOIN version USING (library_id)` to check raw data — version strings must differ character-for-character (`1.0.0` ≠ `1.0.1`).

---

### Query 2 – Risk Distribution Per Project
```sql
SELECT p.name, v.risk_tier, COUNT(*) AS count
FROM project p
JOIN scan s ON s.project_id = p.project_id
JOIN dependency d ON d.scan_id = s.scan_id
JOIN version v ON v.version_id = d.version_id
JOIN library l ON l.library_id = v.library_id
GROUP BY p.project_id, p.name, v.risk_tier;
```
> **✅ Verify:** One row per `(project, risk_tier)` combination with a non-zero count. If empty, check that `risk_tier` is not `NULL` in the `version` table — your semver logic must write `'LOW'`, `'MEDIUM'`, or `'HIGH'` during the scan.

---

### Query 3 – Dependency Churn Between Scans
```sql
-- Dependencies in scan B but not scan A (added)
SELECT l.group_id, l.artifact_id, 'added' AS change
FROM dependency d
JOIN scan s ON s.scan_id = d.scan_id
JOIN version v ON v.version_id = d.version_id
JOIN library l ON l.library_id = v.library_id
WHERE s.scan_id = :scan_b_id
  AND l.library_id NOT IN (
      SELECT v2.library_id FROM dependency d2
      JOIN version v2 ON v2.version_id = d2.version_id
      WHERE d2.scan_id = :scan_a_id)
UNION ALL
-- Dependencies in scan A but not scan B (removed)
SELECT l.group_id, l.artifact_id, 'removed'
FROM dependency d
JOIN scan s ON s.scan_id = d.scan_id
JOIN version v ON v.version_id = d.version_id
JOIN library l ON l.library_id = v.library_id
WHERE s.scan_id = :scan_a_id
  AND l.library_id NOT IN (
      SELECT v2.library_id FROM dependency d2
      JOIN version v2 ON v2.version_id = d2.version_id
      WHERE d2.scan_id = :scan_b_id);
```
> **✅ Verify:** Run two scans of the same project with a `pom.xml` dependency added or removed in between. Substitute real `scan_id` values from `SELECT scan_id, scanned_at FROM scan ORDER BY scanned_at`. Query should return rows labelled `added` or `removed`.

---

## Suggested Build Order

1. `runIde` launches sandbox with "Hello World" in tool window
2. Maven API call → dependency list printed to Run console
3. H2 + Flyway → `flyway_schema_history` shows `SUCCESS = TRUE`
4. `JBTable` wired to H2 → dependencies display in the tool window table
5. Semver logic → `risk_tier` column populated in `version` table
6. Export button → rows appear in Supabase Table Editor
7. Next.js dashboard → both charts render at `localhost:3000`
8. Deploy to Vercel → charts render in incognito at public URL
9. Screenshot all 3 SQL queries with results

---

## Evidence Checklist (for Report)

| Item | What to Capture |
|---|---|
| Plugin structure | `plugin.xml` + Gradle project tree screenshot |
| Sandboxed IDE | Tool window with populated dependency table |
| H2 schema | `CREATE TABLE` DDL from `V1__initial_schema.sql` |
| Sample data | `SELECT *` from each of the 5 tables |
| Flyway | `SELECT * FROM flyway_schema_history` showing `SUCCESS = TRUE` |
| Supabase | Table Editor with exported rows |
| Dashboard | Vercel URL open in incognito, both charts visible |
| SQL queries | Each of the 3 queries + result sets |

---

## Academic Requirements Checklist

- [ ] ≥5 entities with PKs, FKs, cardinalities, and data types in ERD
- [ ] 3NF explained in report
- [ ] Full DDL SQL in report or appendix
- [ ] Sample data in all 5 tables
- [ ] ≥3 multi-table SQL queries shown working
- [ ] ≥2 visualisations on a publicly accessible URL
- [ ] Flyway change management discussed in Deployment section
- [ ] Supabase RLS discussed in security context
- [ ] Vercel URL included in report
