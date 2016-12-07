/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal

import org.gradle.internal.jvm.Jvm

class WorkerDaemonServiceErrorHandlingIntegrationTest extends AbstractWorkerDaemonServiceIntegrationTest {
    def "produces a sensible error when there is a failure in the daemon runnable"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFails

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")

        and:
        failureHasCause("Failure from runnable")
    }

    def "produces a sensible error when there is a failure starting a daemon"() {
        executer.withStackTraceChecksDisabled()
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask) {
                additionalForkOptions = {
                    it.jvmArgs "-foo"
                }
            }
        """

        when:
        fails("runInDaemon")

        then:
        errorOutput.contains(unrecognizedOptionError)

        and:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")

        and:
        failureHasCause("Failed to run Gradle Worker Daemon")
    }

    def "produces a sensible error when a parameter can't be serialized"() {
        withRunnableClassInBuildSrc()

        // Overwrite the Foo class with an un-serializable class
        file('buildSrc/src/main/java/org/gradle/other/Foo.java').text = """
            package org.gradle.other;
            
            public class Foo {
            }
        """

        buildFile << """                        
            task runInDaemon(type: DaemonTask)
        """

        when:
        fails("runInDaemon")

        then:
        failureCauseContains("Possible solutions: params([Ljava.io.Serializable;)")
    }

    def "produces a sensible error when a member of a parameter can't be serialized"() {
        executer.withStackTraceChecksDisabled()
        withRunnableClassInBuildSrc()

        // Create an un-serializable class
        file('buildSrc/src/main/java/org/gradle/other/Bar.java').text = """
            package org.gradle.other;
            
            public class Bar {
            }
        """

        // Overwrite the Foo class with a class with an un-serializable member
        file('buildSrc/src/main/java/org/gradle/other/Foo.java').text = """
            package org.gradle.other;
            
            import java.io.Serializable;
            
            public class Foo implements Serializable {
                private final Bar bar = new Bar();
            }
        """

        buildFile << """                        
            task runInDaemon(type: DaemonTask)
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")
        failureCauseContains("Could not read message")
        failureHasCause("writing aborted; java.io.NotSerializableException: org.gradle.other.Bar")
    }

    def "produces a sensible error even if the action failure cannot be fully serialized"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableWithUnserializableException

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")

        and:
        failureHasCause("Failure from runnable")
    }

    def "produces a sensible error when the runnable cannot be instantiated"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFailsInstantiation

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("You shall not pass!")
    }

    def "produces a sensible error when parameters are incorrect"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableWithDifferentConstructor

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableWithDifferentConstructor.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableWithDifferentConstructor")
        failureHasCause("Could not find any public constructor for class RunnableWithDifferentConstructor which accepts parameters")
    }

    String getUnrecognizedOptionError() {
        def jvm = Jvm.current()
        if (jvm.ibmJvm) {
            return "Command-line option unrecognised: -foo"
        } else {
            return "Unrecognized option: -foo"
        }
    }

    String getRunnableThatFails() {
        return """
            public class RunnableThatFails implements Runnable {
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { }

                public void run() {
                    throw new RuntimeException("Failure from runnable");
                }
            }
        """
    }

    String getRunnableWithUnserializableException() {
        return """
            public class RunnableThatFails implements Runnable {
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { }

                public void run() {
                    throw new BadException("Failure from runnable");
                }
                
                private class Bar { }
                
                private class BadException extends RuntimeException {
                    private Bar bar = new Bar();
                    
                    BadException(String message) {
                        super(message);
                    }
                }
            }
        """
    }

    String getRunnableThatFailsInstantiation() {
        return """
            public class RunnableThatFails implements Runnable {
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { 
                    throw new IllegalArgumentException("You shall not pass!")
                }

                public void run() {
                }
            }
        """
    }

    String getRunnableWithDifferentConstructor() {
        return """
            public class RunnableWithDifferentConstructor implements Runnable {
                public RunnableWithDifferentConstructor(List<String> files, File outputDir) { 
                }

                public void run() {
                }
            }
        """
    }
}
