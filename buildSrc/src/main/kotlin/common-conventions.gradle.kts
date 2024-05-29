val deployVersion = project.findProperty("DeployVersion")
if (deployVersion != null) project.version = deployVersion

val buildSnapshotTrainGradleProperty = findProperty("build_snapshot_train")
extra["build_snapshot_train"] = buildSnapshotTrainGradleProperty != null && buildSnapshotTrainGradleProperty != ""