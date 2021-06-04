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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.libg.command.Command;

public class GrpcGenerator {

	private static final String BND_CACHE_DIR = "~/.bnd/cache";
	private static final Logger log = LoggerFactory.getLogger(GrpcGenerator.class.getName());
	private static final String PROTOC_TARGET_NAME = "protoc";

	private static final String PROTOGEN_PREFIX = "protoc-gen-";

	private static final String GRPC_ID = "grpc-java";
	private static final String GRPC_TARGET_NAME = PROTOGEN_PREFIX + GRPC_ID;

	private static final String RXGRPC_ID = "rxgrpc";
	private static final String RXGRPC_TARGET_NAME = PROTOGEN_PREFIX + RXGRPC_ID;

	private static final String GRPC_OSGI_ID = "grpc-osgi-generator";
	private static final String GRPC_OSGI_TARGET_NAME = PROTOGEN_PREFIX + GRPC_OSGI_ID;

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
			put(RXGRPC_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/rxgrpc-windows-x86_64");
					add("/exe/rxgrpc-osx-x86_64");
					add("/exe/rxgrpc-linux-x86_64");
				}
			});
			put(GRPC_OSGI_TARGET_NAME, new ArrayList<String>() {
				{
					add("/exe/grpc-osgi-generator-windows-x86_64");
					add("/exe/grpc-osgi-generator-osx-x86_64");
					add("/exe/grpc-osgi-generator-linux-x86_64");
				}
			});
		}
	};

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

	private File cacheFile(File cacheDir, String fileName) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("checking cache for file=" + fileName + " in cacheDir=" + cacheDir.getAbsolutePath());
		}
		// Look for target file
		File f = IO.getFile(cacheDir, fileName);
		// if windows and !f.exists() and f not a jar
		if (isWindows() && !f.exists() && !f.getAbsolutePath().endsWith(".jar")) {
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
			if (log.isDebugEnabled()) {
				log.debug("cacheDir=" + cacheDir.getAbsolutePath() + ";targetFileName=" + fileName);
			}
			if (log.isDebugEnabled()) {
				log.debug("copying embedded resource=" + fileName + " to cacheDir=" + cacheDir.getAbsolutePath());
			}
			// copy from the resource to the target file f
			IO.copy(resource, f);
			f.setLastModified(modified);
			// If not windows, set perms
			if (!isWindows()) {
				if (log.isDebugEnabled()) {
					log.debug("setting permissions for file=" + f.getAbsolutePath());
				}
				Files.setPosixFilePermissions(f.toPath(), EnumSet.of(PosixFilePermission.OWNER_EXECUTE,
						PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("cache includes file=" + f.getAbsolutePath());
		}
		return f;
	}

	private File cacheExe(File cacheDir, String targetName) throws IOException {
		File f = cacheFile(cacheDir, targetName);
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

	private String findInArgs(List<String> argsList, String argStart) {
		Optional<String> opt = argsList.stream().filter(arg -> arg.startsWith(argStart)).findFirst();
		return (opt.isPresent()) ? opt.get() : null;
	}

	private boolean osgi = true;
	private boolean grpc = true;
	private File cacheDir;
	private String java_out_dir;

	private void processArgs(String[] args) {
		// deal with args: noosgi means no osgi generation
		List<String> argsList = Arrays.asList(args);
		// Look for 'noosgi' argument
		final String noosgiArg = findInArgs(argsList, "noosgi");
		if (noosgiArg != null) {
			argsList.remove("noosgi");
			osgi = false;
		}
		// 'nogrpc' argument. If 'nogrpc' argument is set then noosgi is implied
		String nogrpcArg = findInArgs(argsList, "nogrpc");
		boolean grpc = true;
		if (nogrpcArg != null) {
			argsList.remove("nogrpc");
			grpc = false;
		}
		// cacheDir argument
		String cacheDirArg = findInArgs(argsList, "cacheDir");
		if (cacheDirArg == null) {
			cacheDirArg = BND_CACHE_DIR;
		} else {
			cacheDirArg = cacheDirArg.split("=")[1];
			argsList.remove("cacheDir");
		}
		// Find --java_out argument and set if not set
		this.java_out_dir = findInArgs(argsList, "--java_out");
		if (this.java_out_dir == null) {
			this.grpc = false;
			this.osgi = false;
		} else {
			this.java_out_dir = java_out_dir.split("=")[1];
		}
		this.cacheDir = IO.getFile(cacheDirArg);
		if (!this.cacheDir.exists()) {
			this.cacheDir.mkdirs();
		}
		if (log.isDebugEnabled()) {
			log.debug("OS=" + System.getProperty("os.name"));
			if (!osgi) {
				log.debug("noosgi is set");
			}
			if (!grpc) {
				log.debug("nogrpc is set");
			}
			log.debug("java_out dir=" + java_out_dir);
		}
	}

	void execute(String[] args) throws Exception {
		processArgs(args);
		// cache protoc exe
		final File protocExe = cacheExe(cacheDir, PROTOC_TARGET_NAME);
		final Command cmd = new Command();
		// add protoc exe path
		cmd.add(protocExe.getAbsolutePath());
		// Add protoc plugins (grpc-java, rxgrpc, grpc-osgi-generator)
		if (grpc) {
			// grpc-java generator protoc plugin...binary
			File grpcExe = cacheExe(cacheDir, GRPC_TARGET_NAME);
			addProtocPlugin(cmd, GRPC_TARGET_NAME, grpcExe.getAbsolutePath(), GRPC_ID, java_out_dir);
			// only add these two if doing osgi
			if (osgi) {
				// rxgrpc
				File rxgrpcExe = cacheExe(cacheDir, RXGRPC_TARGET_NAME);
				addProtocPlugin(cmd, RXGRPC_TARGET_NAME, rxgrpcExe.getAbsolutePath(), RXGRPC_ID, java_out_dir);
				// grpc-osgi-generator
				File grpcOsgiExe = cacheExe(cacheDir, GRPC_OSGI_TARGET_NAME);
				addProtocPlugin(cmd, GRPC_OSGI_TARGET_NAME, grpcOsgiExe.getAbsolutePath(), GRPC_OSGI_ID, java_out_dir);
			}
		}
		// add remaining args from command line
		cmd.add(args);
		// execute
		int execute = cmd.execute(System.in, System.out, System.err);
		if (execute != 0) {
			if (log.isDebugEnabled()) {
				log.debug("ERROR.  resulting errorCode=" + Integer.toString(execute));
			}
			System.exit(execute);
		}
	}

	public static void main(String args[]) throws Exception {
		new GrpcGenerator().execute(args);
	}

}
