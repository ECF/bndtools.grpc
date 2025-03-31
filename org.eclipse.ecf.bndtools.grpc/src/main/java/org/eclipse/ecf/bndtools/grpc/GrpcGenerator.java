/*******************************************************************************
 * Copyright (c) 2020 Composent, Inc. aQute SARL, and others. All rights reserved. 
 * This program and the accompanying materials are made available under the terms 
 * of the Apache Public License v2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Contributors: Composent, Inc., aQute SARL - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.bndtools.grpc;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.libg.command.Command;

/**
 * 
 * GrpcGenerator provides a single entry point for protoc code generation in
 * both java and python given 1 or more proto files.
 * <p>
 * </p>
 * For Java (--java_out argument present) it will generate java source code for
 * 1) protocol buffers messages; 2) grpc service class for impls and client
 * stubs; 3) reactivex-grpc (version 3) code for using the reactivex api with
 * the reactivex-grpc implementation; and 4) the
 * <a href="https://github.com/ECF/">grpc-osgi-generator</a> to create interface
 * classes for the services declared in the proto files.
 * <p>
 * </p>
 * For Python (--python_out argument present) it will generate java source code
 * for 1) protocol buffers messages; 2) grpc service classes for impls and
 * client stubs. This program uses/depends upon an existing install of python
 * runtime 3.9 or greater, and the prior installation of
 * <a href="https://pypi.org/project/grpcio-tools/">grpcio_tools</a> package and
 * all dependencies.
 * <p>
 * </p>
 * Program Arguments
 * <ul>
 * <li><b>[log_level=&lt;slf4j log level&gt;]</b> - Both-Optional (Applies for both Java and Python gen) - Set the log_level for slf4j logger for run of both Java and Python protoc invocations</li>
 * <li><b>[working_dir=&lt;directory&gt;]</b> - Both-Optional - For both Java and Python protoc invocation, sets the working directory to the specified directory</li>
 * <li><b>[cacheDir=&lt;directory&gt;]</b> - Java-Optional - For Java protoc
 * generation, the protoc, grpc-java, reactivex-grpc, and grpc-osgi-generator
 * binaries are copied from inside this bundle to this directory for
 * invocation</li>
 * <li><b>--java_out=&lt;directory&gt;</b> - Java-Required - Directory for protoc generated java code</li>
 * <li><b>[--grpc-java_out=&lt;directory&gt;]</b> - Java-Optional - Directory used for grpc-java generated code. If not set, defaults to value of <b>--java_out</b></li>
 * <li><b>[--grpc-osgi-generated_out=&lt;directory&gt;]</b> - Java-Optional - Directory used for grpc-osgi generated code. If not set, defaults to value of <b>--java_out</b></li>
 * <li><b>[python_exe=&lt;python executable&gt;]</b> - Python-Optional - Python executable. Defaults to 'python', which assumes that python executable is on system path. Note that full path to python executable can be provided...e.g. ~/python</b></li>
 * <li><b>--python_out=&lt;directory&gt;</b> - Python-Required - Directory for grpc_tools.protoc-generated python code</li>
 * <li><b>[--grpc_python_out=&lt;directory&gt;]</b> - Python-Optional - Directory used for grpc-python plugin-generated code. If not set, defaults to value of <b>--python_out</b></li>
 * <li><b>[--pyi_out=&lt;directory&gt;]</b> - Python-Optional - Directory used for pyi-generated code. If not set, pyi file is not generated</b></li>
 * </ul>
 * <p>
 * </p>
 * Example
 * 
 * <pre>
 *  -generate \
    proto; \
        output = src-gen; \
        generate = "org.eclipse.ecf.bndtools.grpc.GrpcGenerator -I=protofiles --java_out=src-gen --python_out=src-gen-python health.proto 2&gt;errors"
 * </pre>
 *
 */
public class GrpcGenerator {

	private static Logger log = null;

	private static final String CACHE_DIR = "cacheDir";
	private static final String BND_CACHE_DIR = "~/.bnd/cache";
	private static final String RXJAVA3 = "rxjava3";

	private static final String PROTOC_TARGET_NAME = "protoc";
	private static final String PROTOGEN_PREFIX = "protoc-gen-";

	private static final String JAVA_OUT_ID = "java_out";

	private static final String GRPC_ID = "grpc-java";
	private static final String GRPC_TARGET_NAME = PROTOGEN_PREFIX + GRPC_ID;

	private static final String RX3GRPC_ID = "rx3grpc";
	private static final String RX3GRPC_TARGET_NAME = PROTOGEN_PREFIX + RX3GRPC_ID;

	private static final String GRPC_OSGI_ID = "grpc-osgi-generator";
	private static final String GRPC_OSGI_TARGET_NAME = PROTOGEN_PREFIX + GRPC_OSGI_ID;

	private static final String PYTHON_ID = "python";
	private static final String PYTHON_EXE_ID = "python_exe";
	private static final String WORKING_DIR_ID = "working_dir";
	private static final String PYTHON_EXE_DEFAULT = "python";
	private static final String GRPC_PYTHON_PROTOC_ID = "grpc_protoc_main";
	private static final String GRPC_PROTOC_FILE = "grpc_protoc.py";

	private static final String GRPC_GENERATOR_PROPERTIES_FILE = "grpcgenerator.properties";

	@SuppressWarnings("serial")
	private Map<String, List<String>> targetExeMap = new HashMap<String, List<String>>() {
		{
			put(PROTOC_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/protoc-windows-x86_64");
					add("/exe/protoc-osx-x86_64");
					add("/exe/protoc-linux-x86_64");
				}
			});
			put(GRPC_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/grpc-java-windows-x86_64");
					add("/exe/grpc-java-osx-x86_64");
					add("/exe/grpc-java-linux-x86_64");
				}
			});
			put(RX3GRPC_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/rx3grpc-windows-x86_64");
					add("/exe/rx3grpc-osx-x86_64");
					add("/exe/rx3grpc-linux-x86_64");
				}
			});
			put(GRPC_OSGI_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/grpc-osgi-generator-windows-x86_64");
					add("/exe/grpc-osgi-generator-osx-x86_64");
					add("/exe/grpc-osgi-generator-linux-x86_64");
				}
			});
			put(GRPC_PROTOC_FILE, new ArrayList<String>() {
				{
					add(GRPC_PROTOC_FILE);
					add(GRPC_PROTOC_FILE);
					add(GRPC_PROTOC_FILE);
				}
			});

		}
	};

	private void debug(String out) {
		if (log.isDebugEnabled()) {
			log.debug(out);
		}
	}

	private String getOsName() {
		return System.getProperty("os.name").toLowerCase();
	}

	private boolean isWindows() {
		return getOsName().startsWith("windows");
	}

	private boolean isMac() {
		String osName = getOsName();
		return osName.startsWith("mac") || osName.startsWith("darwin") || osName.startsWith("osx");
	}

	private boolean isLinux() {
		return getOsName().startsWith("linux");
	}

	private static long getModified() throws NumberFormatException, IOException {
		String timestamp = IO.collect(GrpcGenerator.class.getResource("timestamp.txt"));
		if (timestamp == null || timestamp.equals("${now;long}")) {
			return 0L;
		}
		return Long.parseLong(Strings.trim(timestamp));
	}

	private File cacheFile(File cacheDir, String fileName, boolean exe) throws IOException {
		debug("checking cache for file=" + fileName + " in cacheDir=" + cacheDir.getAbsolutePath());
		// Look for target file
		File f = IO.getFile(cacheDir, fileName);
		// if windows and !f.exists() and f not a jar
		if (exe && isWindows() && !f.exists() && !f.getAbsolutePath().endsWith(".jar")) {
			f = IO.getFile(cacheDir, fileName + ".exe");
		}
		long modified = getModified();
		// if f is not there or it's been recently modified
		if (!f.isFile() || f.lastModified() < modified) {
			// get resource name
			List<String> exeNames = targetExeMap.get(fileName);
			if (exeNames == null)
				throw new IllegalArgumentException("Can't find exes for targetName=" + fileName);
			String name = null;
			// get appropriate for OS
			if (isWindows())
				name = exeNames.get(0);
			else if (isMac()) {
				name = exeNames.get(1);
			} else if (isLinux())
				name = exeNames.get(2);
			// If can't find we return null
			if (name == null)
				return null;
			// Get proto exe resource
			URL resource = GrpcGenerator.class.getResource(name);
			// resource not there...there are problems
			if (resource == null) {
				throw new IllegalArgumentException("Corrupt jar, not found " + name);
			}
			debug("cacheDir=" + cacheDir.getAbsolutePath() + ";targetFileName=" + fileName);
			debug("copying embedded resource=" + fileName + " to cacheDir=" + cacheDir.getAbsolutePath());
			// copy from the resource to the target file f
			IO.copy(resource, f);
			f.setLastModified(modified);
			// If not windows, set perms
			if (!isWindows()) {
				debug("setting permissions for file=" + f.getAbsolutePath());
				Files.setPosixFilePermissions(f.toPath(), EnumSet.of(PosixFilePermission.OWNER_EXECUTE,
						PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			}
		}
		debug("cache includes file=" + f.getAbsolutePath());
		return f;
	}

	private File cacheExe(File cacheDir, String targetName) throws IOException {
		File f = cacheFile(cacheDir, targetName, true);
		if (f == null)
			throw new IllegalArgumentException(
					"Cannot find " + targetName + " to copy to " + cacheDir.getAbsolutePath());
		if (isWindows() && !f.getName().endsWith(".exe")) {
			File moved = new File(cacheDir, targetName + ".exe");
			Files.move(f.toPath(), moved.toPath());
			f = moved;
		}
		return f;
	}

	private void addProtocPlugin(Command cmd, String pluginId, String pluginLocation, String outId, String outValue) {
		StringBuffer sb = new StringBuffer("--plugin=");
		sb.append(pluginId).append("=").append(pluginLocation);
		cmd.add(sb.toString());
		if (outId != null) {
			cmd.add("--" + outId + "_out=" + outValue);
		}
	}

	private boolean java = true;
	private File cacheDir;
	private File working_dir;
	private String java_out_dir;
	private String grpc_out_dir;
	private String rxjava_out_dir;
	private String grpc_osgi_out_dir;
	private boolean python = false;
	private String pythonExe = PYTHON_EXE_DEFAULT;
	private String grpcProtocAbsolutePath;

	private class Args {

		public List<String> javaArgs;
		public List<String> pythonArgs;

		public Args(String[] cmdLineArgs) {
			this.javaArgs = new ArrayList<String>(Arrays.asList(cmdLineArgs));
			this.pythonArgs = new ArrayList<String>(Arrays.asList(cmdLineArgs));
		}

		private List<String> filter(List<String> input, String arg) {
			return input.stream().filter(i -> !i.startsWith(arg)).collect(Collectors.toList());
		}

		public void removeJava(String arg) {
			javaArgs = filter(javaArgs, arg);
		}

		public void removePython(String arg) {
			pythonArgs = filter(pythonArgs, arg);
		}

		public void removeBoth(String arg) {
			removeJava(arg);
			removePython(arg);
		}
	}

	private String findValueForArg(List<String> argList, Properties props, String arg, String def) {
		// First check command line argsList
		Optional<String> opt = argList.stream().filter(arg1 -> arg1.startsWith(arg)).findFirst();
		String value = (opt.isPresent()) ? opt.get() : null;
		if (value == null) {
			value = props.getProperty(arg, def);
		} else if (value.startsWith(arg)) {
			if (value.length() > arg.length()) {
				return value.split("=")[1];
			} else {
				return "true";
			}
		}
		return (value != null) ? value : def;
	}

	private Args processArgs(String[] args) {

		Args allArgs = new Args(args);
		// load properties
		Properties props = new Properties();
		try {
			props.load(new FileReader(new File(GRPC_GENERATOR_PROPERTIES_FILE)));
		} catch (Exception e) {
			System.out.println("Did not read grpcgenerator.properties from working directory");
		}
		// debug argument
		final String logLevelArgVal = findValueForArg(allArgs.javaArgs, props, "log_level", null);
		if (logLevelArgVal != null) {
			allArgs.removeBoth("log_level");
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevelArgVal);
		}
		log = LoggerFactory.getLogger(this.getClass());
		debug("properties loaded from grpcgenerator.properties=" + props);

		// cacheDir argument
		final String cacheDirArg = findValueForArg(allArgs.javaArgs, props, CACHE_DIR, BND_CACHE_DIR);
		allArgs.removeBoth(CACHE_DIR);
		// Create cacheDir/targetDir
		this.cacheDir = IO.getFile(cacheDirArg);
		if (!this.cacheDir.exists()) {
			this.cacheDir.mkdirs();
		}
		debug("cacheDir=" + this.cacheDir.getAbsolutePath());
		// working_dir argument
		final String workingDirArg = WORKING_DIR_ID;
		String wdVal = findValueForArg(allArgs.javaArgs, props, workingDirArg, null);
		if (wdVal != null) {
			File f = new File(wdVal);
			if (f.exists() && f.isDirectory() && f.canWrite()) {
				this.working_dir = f;
			} else {
				throw new IllegalArgumentException("working_dir=" + f.getAbsolutePath() + " is not valid");
			}
		}
		// remove from python args
		allArgs.removeBoth(workingDirArg);
		if (this.working_dir != null) {
			debug("working_dir=" + this.working_dir.getAbsolutePath());
		}
		// rx3grpc argument...ignore and remove
		allArgs.removeBoth(RXJAVA3);
		allArgs.removeBoth("rxjava");

		// --java_out argument *required* for java run of protoc
		final String javaOutArg = "--" + JAVA_OUT_ID;
		this.java_out_dir = findValueForArg(allArgs.javaArgs, props, javaOutArg, null);
		if (this.java_out_dir == null) {
			this.java = false;
		} else {
			// remove from python args
			allArgs.removePython(javaOutArg);

			// --grpc_out argument
			final String grpc_out_param = "--" + GRPC_ID + "_out";
			this.grpc_out_dir = findValueForArg(allArgs.javaArgs, props, grpc_out_param, null);// findInArgs(bothArgs.javaArgs,
																								// grpc_out_param);
			if (grpc_out_dir == null) {
				grpc_out_dir = this.java_out_dir;
			}
			allArgs.removePython(grpc_out_param);
			// --rx3grpc_out
			final String rx_out_param = "--" + RX3GRPC_ID + "_out";
			this.rxjava_out_dir = findValueForArg(allArgs.javaArgs, props, rx_out_param, null);
			if (rxjava_out_dir == null) {
				rxjava_out_dir = java_out_dir;
			}
			allArgs.removePython(rx_out_param);
			// --grpc-osgi-generator_out argument
			final String osgi_gen_out_param = "--" + GRPC_OSGI_ID + "_out";
			this.grpc_osgi_out_dir = findValueForArg(allArgs.javaArgs, props, osgi_gen_out_param, null);
			if (this.grpc_osgi_out_dir == null) {
				this.grpc_osgi_out_dir = java_out_dir;
			}
			allArgs.removePython(osgi_gen_out_param);
		}

		// python
		final String pythonOutArg = "--" + PYTHON_ID + "_out";
		final String pythonGrpcArg = "--" + "grpc_python" + "_out";
		final String pyiArg = "--" + "pyi" + "_out";
		final String pythonExeArg = PYTHON_EXE_ID;
		final String grpcProtocArg = "--" + GRPC_PYTHON_PROTOC_ID;

		// arg: --python_out=<path>
		final String pythonArgValue = findValueForArg(allArgs.pythonArgs, props, pythonOutArg, null);
		if (pythonArgValue != null) {
			this.python = true;
			// remove from java
			allArgs.removeJava(pythonOutArg);

			// arg python_exe=<dir>
			this.pythonExe = findValueForArg(allArgs.pythonArgs, props, pythonExeArg, "python");
			allArgs.removeBoth(pythonExeArg + "=" + this.pythonExe);
			
			allArgs.removeJava(pythonGrpcArg);
			
			// arg --grpc_protoc_main
			this.grpcProtocAbsolutePath = findValueForArg(allArgs.pythonArgs, props, grpcProtocArg, null);
			if (this.grpcProtocAbsolutePath == null) {
				try {
					File f = cacheFile(this.cacheDir, GRPC_PROTOC_FILE, false);
					this.grpcProtocAbsolutePath = f.getAbsolutePath();
				} catch (IOException e) {
					// Should not happen
					if (log.isErrorEnabled()) {
						log.error("Could not get read grpc_protoc.py into cacheDir", e);
					}
				}
			}
			allArgs.removeBoth(grpcProtocArg);

			allArgs.removeJava(pyiArg);
		}
		return allArgs;
	}

	private void addGrpcOsgiPlugin(Command cmd, File grpcOsgiExe, String out_dir) {
		StringBuffer sb = new StringBuffer("--plugin=");
		sb.append(GRPC_OSGI_TARGET_NAME).append("=").append(grpcOsgiExe.getAbsolutePath());
		cmd.add(sb.toString());
		cmd.add("--" + GRPC_OSGI_ID + "_opt=" + RXJAVA3);
		cmd.add("--" + GRPC_OSGI_ID + "_out=" + out_dir);
	}

	private void execJava(List<String> javaArgs) throws Exception {
		// cache protoc exe
		final File protocExe = cacheExe(cacheDir, PROTOC_TARGET_NAME);
		final Command cmd = new Command();
		// add protoc exe path
		cmd.add(protocExe.getAbsolutePath());
		if (this.working_dir != null) {
			cmd.setCwd(this.working_dir);
		}
		// Add protoc plugins (grpc-java, rxgrpc, grpc-osgi-generator)
		// grpc-java generator protoc plugin...binary
		File grpcExe = cacheExe(cacheDir, GRPC_TARGET_NAME);
		addProtocPlugin(cmd, GRPC_TARGET_NAME, grpcExe.getAbsolutePath(), GRPC_ID, this.grpc_out_dir);
		String rxgrpcTargetName = RX3GRPC_TARGET_NAME;
		String rxgrpcId = RX3GRPC_ID;
		// rxgrpc
		File rxgrpcExe = cacheExe(cacheDir, rxgrpcTargetName);
		addProtocPlugin(cmd, rxgrpcTargetName, rxgrpcExe.getAbsolutePath(), rxgrpcId, this.rxjava_out_dir);
		// grpc-osgi-generator
		File grpcOsgiExe = cacheExe(cacheDir, GRPC_OSGI_TARGET_NAME);
		addGrpcOsgiPlugin(cmd, grpcOsgiExe, this.grpc_osgi_out_dir);
		// add remaining args from command line
		cmd.add(javaArgs.toArray(new String[] {}));
		// execute java generator
		int executeJavaResult = cmd.execute(System.in, System.out, System.err);
		if (executeJavaResult != 0) {
			debug("ERROR running protoc for java generation.  resulting errorCode="
					+ Integer.toString(executeJavaResult));
			System.exit(executeJavaResult);
		}
	}

	private void execPython(List<String> pythonArgs) throws Exception {
		Command cmdPython = new Command();
		if (this.working_dir != null) {
			cmdPython.setCwd(this.working_dir);
		}
		cmdPython.add(this.pythonExe);
		cmdPython.add(this.grpcProtocAbsolutePath);

		cmdPython.add(pythonArgs.toArray(new String[] {}));
		int executePythonResult = cmdPython.execute(System.in, System.out, System.err);
		if (executePythonResult != 0) {
			debug("ERROR running python generation. resulting errorCode=" + Integer.toString(executePythonResult));
		}
		System.exit(executePythonResult);
	}

	void execute(String[] args) throws Exception {
		Args allArgs = processArgs(args);

		if (this.java) {
			execJava(allArgs.javaArgs);
		}
		// Now run python if options set
		if (python) {
			execPython(allArgs.pythonArgs);
		}
	}

	public static void main(String args[]) throws Exception {
		new GrpcGenerator().execute(args);
	}

}
