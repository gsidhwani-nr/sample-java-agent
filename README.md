

# Sample Java Agent

Only for demo purposes.

### Sample Reference

Java Agent on SAP NetWeaver: [Configure SAP NetWeaver to Use the Java Agent](https://techdocs.broadcom.com/us/en/ca-enterprise-software/devops/devtest-solutions/10-7/agents/devtest-java-agent/java-agent-installation/platform-specific-agent-configuration/configure-sap-netweaver-to-use-the-java-agent.html)

### Command to Run the Application

```bash
java -javaagent:aix-java-agent-test/target/aix-java-agent-test-1.0-SNAPSHOT.jar -cp "../SimpleApp/bin:." com.newrelic.labs.sample.SimpleApp
```

### Notes

- Ensure that the path to the Java agent JAR (`aix-java-agent-test/target/aix-java-agent-test-1.0-SNAPSHOT.jar`) is correct.
- Adjust the classpath (`-cp "../SimpleApp/bin:.") to include all necessary directories and JAR files for your application.

