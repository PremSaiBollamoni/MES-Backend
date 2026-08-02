# GTMES API Reference

Base path `/api`. All endpoints except `/api/auth/health` and `/api/auth/login` require
`Authorization: Bearer <token>`. Endpoints marked with an activity are additionally gated by
`@RequiresActivity` — the caller's active role must hold that activity or the request is
rejected.

Dates are `YYYY-MM-DD`. Times are `HH:mm:ss`.

---

## Authentication — `/api/auth`

| Method | Path | Activity | Description |
|---|---|---|---|
| `GET` | `/health` | — | Liveness check |
| `POST` | `/login` | — | Authenticate with username and password |
| `POST` | `/select-role` | — | Exchange a pre-auth token for a session token |
| `POST` | `/switch-role` | — | Change the active role of a signed-in session |

### `POST /api/auth/login`

```json
{ "username": "MES007", "password": "••••••" }
```

Multi-role response — no session token yet:

```json
{
  "requiresRoleSelection": true,
  "preAuthToken": "eyJhbGciOiJI…",
  "id": 7,
  "username": "MES007",
  "name": "PREM SAI BOLLAMONI",
  "roles": [
    { "roleId": 1, "roleName": "FACTORY_MANAGER" },
    { "roleId": 5, "roleName": "PRODUCTION_SUPERVISOR" }
  ]
}
```

Single-role users receive a session token directly instead.

### `POST /api/auth/select-role`

```json
{ "preAuthToken": "eyJhbGciOiJI…", "roleName": "FACTORY_MANAGER" }
```

Returns the session token plus the resolved user and the activities the role permits.
`roleName` is required — a `roleId` is not accepted.

---

## Employees — `/api/employees`

Activity: `EMPLOYEE_LIST` for reads, `EMPLOYEE_FORM` for writes.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | List all employees |
| `GET` | `/{id}` | Employee detail |
| `POST` | `/` | Create an employee |
| `PUT` | `/{id}` | Update an employee |
| `PUT` | `/{id}/roles` | Replace the employee's role assignments |
| `PATCH` | `/{id}/deactivate` | Mark inactive |
| `PATCH` | `/{id}/reactivate` | Mark active |
| `POST` | `/{id}/face` | Upload a face image (`multipart/form-data`) |
| `POST` | `/import-from-hrms` | Import one employee from the biometric system |
| `POST` | `/import-from-hrms/bulk` | Import several in one call |
| `DELETE` | `/{id}` | Delete an employee |
| `POST` | `/bulk-delete` | Delete several by id |

---

## Roles — `/api/roles`

Activity: `ROLE_LIST` for reads, `ROLE_FORM` for writes.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Roles available for assignment |
| `GET` | `/manage` | Full role list for administration |
| `GET` | `/manage/{id}` | Role detail with its activities |
| `GET` | `/manage/activities` | Every activity that can be granted |
| `POST` | `/manage` | Create a role |
| `PUT` | `/manage/{id}` | Update a role and its activities |
| `DELETE` | `/manage/{id}` | Delete a role |
| `POST` | `/manage/bulk-delete` | Delete several roles |

---

## Stations — `/api/stations`

Activity: `STATION_LIST` for reads, `STATION_FORM` for writes.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | All stations ordered by station number |
| `GET` | `/{id}` | Station detail |
| `POST` | `/` | Create a station |
| `PUT` | `/{id}` | Update a station |
| `DELETE` | `/{id}` | Delete a station |
| `POST` | `/bulk-delete` | Delete several stations |

A station carries `stationNumber`, `operationName`, `zone` (`S1`/`S2`), `sam`, `operatorCount`,
`targetPiecesPerHour`, `targetPiecesPerDay`, `ratePerPiece`, `minStockThreshold`,
`maxStockThreshold` and `active`. Every production figure derives from these values.

---

## Attendance — `/api/attendance`

Activity: `ATTENDANCE`.

| Method | Path | Query | Description |
|---|---|---|---|
| `GET` | `/live` | — | Today's punches as they arrive |
| `GET` | `/daily` | `date` | Full presence record for a date |
| `GET` | `/history` | | Attendance across a period |
| `POST` | `/sync` | | Pull fresh attendance from the biometric system |

## HRMS — `/api/hrms`

| Method | Path | Description |
|---|---|---|
| `GET` | `/status` | Whether the integration is configured and reachable |
| `GET` | `/employees` | Employees known to the biometric system |
| `GET` | `/employees/unlinked` | Present in HRMS but not yet in GTMES |

---

## Operations

All endpoints below require the `OPERATIONS` activity.

### Allocations — `/api/allocations`

| Method | Path | Query / Body | Description |
|---|---|---|---|
| `GET` | `/` | `date` | Allocations for a shift date |
| `GET` | `/roster` | `date` | Employees available to allocate |
| `POST` | `/` | allocation body | Assign operators to a station |
| `DELETE` | `/{id}` | — | Remove an allocation |

### Tracking — `/api/tracking`

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Sessions for a date |
| `POST` | `/start` | Open a timed work session |
| `POST` | `/{id}/stop` | Close it and write a production entry |

```json
POST /api/tracking/start
{ "employeeIds": [205], "stationId": 39, "shiftDate": "2026-08-02", "piecesIssued": 120 }
```

```json
POST /api/tracking/123/stop
{ "piecesCompleted": 118 }
```

Elapsed time is measured to the second and carried into the resulting production entry in
preference to any duration derived from clock times.

### Production entries — `/api/production-entries`

| Method | Path | Query | Description |
|---|---|---|---|
| `GET` | `/` | `date` | All entries for a date |
| `GET` | `/me` | `days` | The caller's own entries — requires `MY_PRODUCTION` |
| `POST` | `/` | — | Log an entry |

```json
POST /api/production-entries
{
  "employeeId": 205,
  "stationId": 39,
  "shiftDate": "2026-08-02",
  "startTime": "10:00:00",
  "endTime": "10:20:00",
  "piecesProduced": 45
}
```

```json
{
  "id": 86, "employeeId": 205, "employeeName": "Venkatalakshmi",
  "stationId": 39, "stationNumber": "STATION-39", "operationName": "Polybag insertiom",
  "shiftDate": "2026-08-02", "startTime": "10:00:00", "endTime": "10:20:00",
  "piecesProduced": 45, "timeOnMachineMinutes": 20.0,
  "samEarned": 18.0, "wagesEarned": 22.5,
  "efficiencyPct": 90.0, "classification": "MARGINAL"
}
```

Duration prefers an explicit `timeOnMachineMinutes` when supplied (the tracking flow); otherwise
it is derived from `startTime` and `endTime`. `startTime`/`endTime` are optional — entries
recorded before hourly capture existed have neither.

**Validation.** Overlapping times for the same employee on the same date are rejected with
`ERR_TIME_OVERLAP`; an end at or before the start with `ERR_END_BEFORE_START`; a negative count
with `ERR_PIECES_NEGATIVE`.

### Station status — `/api/station-status`

`GET /` with `date` — every active station with its manned state, the allocation if any, and
the bottleneck (highest SAM) flagged as critical when unmanned.

### Stock availability — `/api/stock-availability`

`GET /` with `date`, `POST /` to log availability per station with a reason where absent.

### Stock position — `/api/stock-position`

`GET /` with `date`, `POST /` to record `openingBalance`, `additions` and `dispatched` per zone.

### WIP / bundles — `/api/wip`

| Method | Path | Description |
|---|---|---|
| `GET` | `/bundles` | Bundles with current station and status |
| `POST` | `/bundles` | Create a bundle |
| `POST` | `/bundles/{id}/move` | Move it to another station |
| `POST` | `/bundles/{id}/complete` | Mark it completed |
| `GET` | `/summary` | Queue depth per station |

---

## Dashboard — `/api/dashboard`

`GET /summary` (activity `DASHBOARD`) — headcount totals, distribution by role and department,
and recent joiners.

## Profile — `/api/profile`

Activity `PROFILE`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | The signed-in employee's profile |
| `PUT` | `/` | Update own profile |
| `PUT` | `/password` | Change own password |

---

## Reports — `/api/reports`

Activity `REPORTS`.

| Method | Path | Query | Description |
|---|---|---|---|
| `GET` | `/` | `type` | Reports already generated for a type |
| `POST` | `/generate` | `date`, `type`, `force` | Generate or return a stored report |
| `GET` | `/{id}/pdf` | — | Download the rendered PDF |

`type` is one of `PRODUCTION`, `STOCK`, `ATTENDANCE`, `WORKER_PERFORMANCE` (default
`PRODUCTION`). `force` defaults to `false`; a report already generated for that date and type is
returned as-is rather than regenerated, so a document shared with stakeholders does not change
on next viewing.

**Status codes.** `503` when reporting is disabled or no credential is configured; `502` when
generation itself fails.
