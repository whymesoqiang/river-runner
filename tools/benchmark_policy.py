#!/usr/bin/env python3
"""Run seeded compatibility checks and a JVM benchmark against the original policy.
Requires JDK 17, network access for Gradle dependencies, and a Git checkout with the baseline commit.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline', default='3ce630d3d612085001fd242112f7922b07a5de6b')
args = parser.parse_args()
original = subprocess.check_output(
    ['git', 'show', f'{args.baseline}:engine/src/commonMain/kotlin/com/riverrunner/engine/Bot.kt'],
    cwd=root, text=True,
)
# Share model types; retain the original decision/sampling implementation unchanged.
reference = original[:original.index('/**\n * Player styles.')] + original[original.index('object Bot {'):].replace('object Bot {', 'object BaselineBot {', 1)
with tempfile.TemporaryDirectory(prefix='river-runner-policy-') as directory:
    project = Path(directory)
    tests = project / 'src/test/kotlin/com/riverrunner/engine'
    tests.mkdir(parents=True)
    (tests / 'BaselineBot.kt').write_text(reference)
    shutil.copy(root / 'tools/performance/ComparisonTest.kt', tests)
    # Copy current engine sources to avoid path interpolation in Gradle Kotlin strings.
    shutil.copytree(root / 'engine/src/commonMain/kotlin', project / 'src/main/kotlin')
    (project / 'settings.gradle.kts').write_text('pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }\n')
    (project / 'build.gradle.kts').write_text('''plugins { kotlin("jvm") version "2.2.21" }
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test-junit5")); testRuntimeOnly("org.junit.platform:junit-platform-launcher") }
tasks.test { useJUnitPlatform(); testLogging { events("passed", "failed"); showStandardStreams = true } }
''')
    subprocess.run([str(root / 'gradlew'), '-p', str(project), 'test', '--no-daemon', '--console=plain'], check=True)
