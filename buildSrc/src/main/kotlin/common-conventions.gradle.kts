val deployVersion = project.findProperty("DeployVersion")
if (deployVersion != null) project.version = deployVersion