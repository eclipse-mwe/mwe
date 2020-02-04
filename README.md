# Modeling Workflow Engine (MWE)
See the [MWE2 documentation](documentation/mwe2.md) for more details.

## Release Process

MWE releases are performed via the master branch on [Jenkins](https://ci.eclipse.org/mwe/job/mwe2/job/master/). The job is parameterized so the release type ('Integration', 'Beta', 'M1', 'M2', 'M3', 'RC1', 'RC2', 'GA') can be configured upon starting the job.

### Nightly Integration Releases

The master branch is configured to perform a nightly integration build. If a nightly build fails, running the build manually choosing the 'Integration' release type should return the same result and further actions should not be necessary.

### Stable Releases

Performing a stable release has a few manual steps that are documented here:

1. Start the master branch job, selecting the release type. When the job is finished, artifacts should have been uploaded to the [Sonatype Nexus](https://oss.sonatype.org) and the Eclipse download storage.

1. Check Jenkins artifacts, if Maven artifacts have the correct versions (without qualifiers!).

1. Check Jenkins artifacts, if p2 artifacts have the correct version (with qualifiers!).

1. Check if the [MWE download site](https://www.eclipse.org/modeling/emft/downloads/index.php?project=mwe&showAll=1&showMax=5&sortBy=) now provides the correct artifacts.

1. Check if the [MWE update site](https://download.eclipse.org/modeling/emft/mwe/updates) now provides the correct artifacts.

1. Check if Sonatype Nexus provides the correct artifacts. If so, close the staging repository and release it afterwards.

1. Create a SimRel contribution by replacing the update site in [emft-mwe.aggrcon](https://git.eclipse.org/c/simrel/org.eclipse.simrel.build.git/tree/emft-mwe.aggrcon) with the newly released version.

1. If the release was of the type 'GA', run the `./set_version.sh` script and provide a PR to prepare the next release cycle.

### Build Locally

`mvn -f maven/org.eclipse.emf.mwe2.parent/pom.xml -Dtycho.localArtifacts=ignore clean install`