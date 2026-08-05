# SAP PO → New Relic Monitor — POC Deployment Guide

## What This Does

Connects to your SAP Process Orchestration (PO) 7.x system and sends message monitoring data to New Relic every 5 minutes:

- **Message counts** by status (errors, waiting, processing, successful)
- **Per-interface breakdown** (sender, receiver, interface name, error counts)
- **Error rate** and trends over time
- **Individual message IDs** with error details (optional, for low-volume systems)

No SAP software changes required. Uses built-in SAP PO monitoring APIs (read-only).

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java | Version 8 or higher — verify with `java -version` |
| Network | This server must reach SAP PO on port 50000 |
| SAP User | Needs role `SAP_XI_MONITOR` or `SAP_XI_MONITOR_J2EE` |
| New Relic | Ingest License Key + Account ID |

---

## Files in This Package

```
sappo-monitor-1.0.0-all.jar   ← the application (no other dependencies needed)
config.properties.qce          ← configuration template for QCE environment
README.md                      ← this file
```

---

## Step 1 — Configure

Copy and rename the config file:

**Windows:**
```cmd
copy config.properties.qce config.properties
```

**Linux:**
```bash
cp config.properties.qce config.properties
```

Open `config.properties` and fill in the required values:

```properties
# SAP PO connection
sappo.host=<your-SAP-PO-hostname-or-IP>
sappo.port=50000
sappo.username=<monitoring-user>
sappo.password=<password>

# New Relic
newrelic.insertKey=<your-NR-ingest-license-key>
newrelic.accountId=<your-NR-account-id>
```

> **Where to find your NR keys:**
> - Insert Key: NR UI → top-right menu → API Keys → type `INGEST - LICENSE`
> - Account ID: NR UI → Account Settings (also visible in the URL)

---

## Step 2 — Verify SAP PO Connectivity

Run all 4 URLs below in a browser on the SAP server (or any machine that can reach it).
Log in with the SAP monitoring user credentials when prompted.

---

### URL 1 — Get Component List
```
http://<HOST>:50000/mdt/messageoverviewqueryservlet
```
**Expected:** XML response with `<XIComponents>` listing the PI components on this system.
Note the `af.*` component name — you will need it in URLs 2 and 3.

```xml
<!-- Example response -->
<XIComponents>
    <Component>af.pd1.hostname</Component>   ← note this value
    <Component>igw.pd1.hostname</Component>
    <Component>BS_ECC_ECD_CLNT_800</Component>
</XIComponents>
```

---

### URL 2 — Get Valid Time Window for a Component
```
http://<HOST>:50000/mdt/messageoverviewqueryservlet?component=<AF-COMPONENT>
```
Replace `<AF-COMPONENT>` with the `af.*` value from URL 1.

**Expected:** XML with `<Views>` and `<Periods>`. Note the first `<Begin>` and `<End>` under `<Type>HOURLY</Type>` — you need them for URL 3.

```xml
<!-- Example — copy these timestamps -->
<Period>
    <Type>HOURLY</Type>
    <Interval>
        <Begin>2026-08-05T10:00:00.000-04:00</Begin>   ← copy
        <End>2026-08-05T11:00:00.000-04:00</End>       ← copy
    </Interval>
</Period>
```

---

### URL 3 — Get Message Statistics
```
http://<HOST>:50000/mdt/messageoverviewqueryservlet?component=<AF-COMPONENT>&view=SR_ENTRY_VIEW_XPI&begin=<BEGIN>&end=<END>&detailedStatus=true
```
Replace `<AF-COMPONENT>`, `<BEGIN>`, and `<END>` with values from URLs 1 and 2.

**Expected:** `<Code>OK</Code>` with message counts per sender/receiver/interface.

```xml
<!-- Example success response -->
<Result><Code>OK</Code></Result>
<Data>
    <DataRows>
        <Row>
            <Entry>BS_FILE2IDOC1_CUST</Entry>   <!-- Sender -->
            <Entry>BS_ECC_ECD_CLNT_800</Entry>  <!-- Receiver -->
            <Entry>SI_FileToIDoc</Entry>         <!-- Interface -->
            <Entry>5</Entry>                     <!-- System Error count -->
            <Entry>3</Entry>                     <!-- Waiting count -->
        </Row>
    </DataRows>
</Data>
```

---

### URL 4 — Verify SOAP API (Individual Messages)
```
http://<HOST>:50000/AdapterMessageMonitoring/basic?wsdl&mode=ws_policy&style=document
```
**Expected:** An XML WSDL document starting with `<wsdl:definitions ...>`.

If this returns a WSDL, individual message IDs can also be pulled. If it returns 404, only aggregate data will be collected (still sufficient for monitoring dashboards).

---

### Summary Checklist

| Check | URL | Pass Condition |
|---|---|---|
| Component list | URL 1 | `<XIComponents>` with `af.*` entries |
| Time window | URL 2 | `<Period><Type>HOURLY</Type>` present |
| Message data | URL 3 | `<Code>OK</Code>` with `<DataRows>` |
| SOAP API | URL 4 | WSDL XML returned (optional) |

Share the results of all 4 URLs with your New Relic team before proceeding.

---

## Step 3 — Run

**Windows (foreground — good for initial test):**
```cmd
java -jar sappo-monitor-1.0.0-all.jar
```

**Windows (background service using NSSM):**
```cmd
nssm install SapPoMonitor java "-jar C:\sappo\sappo-monitor-1.0.0-all.jar"
nssm set SapPoMonitor AppDirectory C:\sappo
nssm set SapPoMonitor Start SERVICE_AUTO_START
nssm start SapPoMonitor
```
Download NSSM free from: https://nssm.cc/download

**Linux (background):**
```bash
nohup java -jar sappo-monitor-1.0.0-all.jar > sappo.log 2>&1 &
```

**Linux (systemd service):**
```bash
sudo nano /etc/systemd/system/sappo-monitor.service
```
```ini
[Unit]
Description=SAP PO Monitor for New Relic

[Service]
WorkingDirectory=/opt/sappo
ExecStart=/usr/bin/java -jar /opt/sappo/sappo-monitor-1.0.0-all.jar
Restart=always
RestartSec=30

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl enable sappo-monitor
sudo systemctl start sappo-monitor
```

---

## Step 4 — Verify Data in New Relic

Within 5–10 minutes of starting, run these queries in **NR Query Builder**:

**Check metrics are arriving:**
```sql
SELECT latest(sappo.messages.total) AS 'Total',
       latest(sappo.messages.error) AS 'Errors',
       latest(sappo.messages.errorRate) AS 'Error Rate %'
FROM Metric
SINCE 10 minutes ago
```

**Check interface-level data:**
```sql
SELECT latest(totalCount) AS 'Total',
       latest(systemErrorCount) AS 'Errors',
       latest(waitingCount) AS 'Waiting'
FROM SapPoMessageFlow
FACET senderComponent, receiverComponent, interfaceName
SINCE 1 hour ago
ORDER BY latest(systemErrorCount) DESC
```

**Check individual message events (if SOAP enabled):**
```sql
SELECT messageGuid, status, interfaceName, errorCategory, errorText
FROM SapPoMessage
SINCE 1 hour ago
ORDER BY timestamp DESC
LIMIT 50
```

---

## Configuration Reference

| Key | Default | Description |
|---|---|---|
| `sappo.host` | — | SAP PO hostname or IP (required) |
| `sappo.port` | 50000 | SAP PO HTTP port |
| `sappo.ssl` | false | Set true for HTTPS |
| `sappo.username` | — | SAP monitoring user (required) |
| `sappo.password` | — | SAP user password (required) |
| `sappo.maxMessages` | 200 | Max messages per SOAP call |
| `newrelic.insertKey` | — | NR Ingest License Key (required) |
| `newrelic.accountId` | — | NR Account ID (required) |
| `poll.intervalSeconds` | 300 | How often to poll SAP PO (seconds) |
| `poll.lookbackMinutes` | 1 | Time window per poll |
| `poll.soapMultiplier` | 0 | 0 = SOAP disabled (recommended for >5K msg/hr) |
| `poll.soapOnlyErrors` | true | SOAP fetches error messages only |
| `poll.soapMaxMessages` | 100 | Hard cap on SOAP message fetch |
| `app.env` | production | Environment label on all NR data |

---

## Performance Impact on SAP PO

| API Used | SAP Impact | Notes |
|---|---|---|
| `MessageOverviewQueryServlet` | **Minimal** | Pre-computed hourly stats |
| `AdapterMessageMonitoringVi` SOAP | **Moderate** | Live DB query — disabled by default for high-volume |

For systems processing **>5,000 messages/hour**, keep `poll.soapMultiplier=0`.

---

## Troubleshooting

**App starts but shows total=0:**
- Check the SAP PO hourly interval in the logs — data is aggregated hourly
- Try a different hour: look for messages in the MDT UI for the same hour

**Authentication error (401):**
- Verify `sappo.username` and `sappo.password` in config.properties
- Confirm the user has `SAP_XI_MONITOR` role in SAP UME

**No data in New Relic after 10 minutes:**
- Check logs for errors
- Verify `newrelic.insertKey` is an `INGEST - LICENSE` key (not API key)
- Confirm `newrelic.accountId` matches the account where you're querying

**Port 50000 unreachable:**
- Confirm firewall allows outbound HTTP from this server to SAP PO host on port 50000
- Test: `telnet <sap-host> 50000` or `Test-NetConnection -ComputerName <sap-host> -Port 50000`

---

## Data Sent to New Relic

| Event / Metric | Type | Content |
|---|---|---|
| `sappo.messages.*` | Metric (gauge) | Total, error, waiting, success counts + error rate |
| `SapPoMessageFlow` | Custom Event | Per-interface counts with sender/receiver/namespace |
| `SapPoMessage` | Custom Event | Individual message GUID, status, error details (SOAP only) |

---

*For questions or issues, contact your New Relic account team.*
