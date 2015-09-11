/*
 * Copyright (C) 2013 Samuel Halliday
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/].
 */
package com.github.fommil.netlib.generator;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.thoughtworks.paranamer.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.List;
import java.util.Collection;
import java.nio.*;

public abstract class AbstractNetlibGenerator extends AbstractMojo {

  /**
   * Location of the generated source files.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/netlib-java", required = true)
  protected File outputDir;

  @Parameter(required = true)
  protected String outputName;

  /**
   * The artifact of the jar to generate from.
   * Note that this must be listed as a <code>dependency</code>
   * section of the calling module, not a plugin <code>dependency</code>.
   */
  @Parameter(defaultValue = "net.sourceforge.f2j:arpack_combined_all:jar:0.1", required = true)
  protected String input;

  /**
   * The artifact of the javadocs to extract parameter names.
   * Note that this must be listed as a <code>dependency</code>
   * section of the calling module, not a plugin <code>dependency</code>.
   */
  @Parameter(defaultValue = "net.sourceforge.f2j:arpack_combined_all:jar:javadoc:0.1")
  protected String javadoc;

  /**
   * The package to scan.
   */
  @Parameter(required = true)
  protected String scan;

  /**
   * Method names to exclude (regex);
   */
  @Parameter
  protected String exclude;

  @Component
  protected MavenProject project;

  protected File getFile(String artifactName) {
    // artifactMap is a bit too simplistic
    for (Artifact artifact : project.getArtifacts())
      if (artifact.toString().startsWith(artifactName))
        return artifact.getFile();
    throw new IllegalArgumentException("could not find " + artifactName + " in " + project.getArtifacts());
  }

  /**
   * Implementation specific interpretation of the parameters.
   *
   * @param methods obtained from a scan of F2J public static methods.
   * @return the file contents for the generated file associated to the parameters.
   * @throws Exception
   */
  abstract protected String generate(List<Method> methods) throws Exception;

  protected Paranamer paranamer = new DefaultParanamer();

  @Override
  public void execute() throws MojoExecutionException {
    try {
      project.addCompileSourceRoot(outputDir.getAbsolutePath());
      File output = new File(outputDir, outputName);
      if (output.exists() && project.getFile().lastModified() < output.lastModified()) {
        getLog().info("No changes detected, skipping: " + output);
        return;
      }

      if (Strings.isNullOrEmpty(javadoc))
        getLog().warn("Javadocs not attached for paranamer.");
      else
        paranamer = new CachingParanamer(new JavadocParanamer(getFile(javadoc)));

      File jar = getFile(input);

      JarMethodScanner scanner = new JarMethodScanner(jar);

      List<Method> methods = Lists.newArrayList(
          Iterables.filter(scanner.getStaticMethods(scan), new Predicate<Method>() {
            @Override
            public boolean apply(Method input) {
              return exclude == null || !input.getName().matches(exclude);
            }
          }));

      String generated = generate(methods);

      output.getParentFile().mkdirs();

      getLog().info("Generating " + output.getAbsoluteFile());
      Files.write(generated, output, Charsets.UTF_8);
    } catch (Exception e) {
      throw new MojoExecutionException("java generation", e);
    }
  }


  /**
   * @param method
   * @return parameters names for the netlib interface.
   */
  protected List<String> getNetlibJavaParameterNames(Method method, boolean offsets) {
    final List<String> params = Lists.newArrayList();
    iterateRelevantParameters(method, offsets, new ParameterCallback() {
      @Override
      public void process(int i, Class<?> param, String name, String offsetName) {
        params.add(name);
      }
    });
    return params;
  }

  protected enum VectorParamOutputVariant {
    ARRAY,
    NIOBUFFER,
    POINTER_AS_LONG,
    // XXX generate a f2j wrapper for pointers, doing copies + unsafe (not
    // great but better than no implementation, I guess)
  }

  protected VectorParamOutputVariant[] vectorParamOutputVariants() {
    return VectorParamOutputVariant.class.getEnumConstants();
  }

  /**
   * @param method
   * @return canonical parameter types for the netlib interface.
   */
  protected List<String> getNetlibJavaParameterTypes(Method method, boolean offsets, final VectorParamOutputVariant vectorType) {
    final List<String> types = Lists.newArrayList();
    iterateRelevantParameters(method, offsets, new ParameterCallback() {
      @Override
      public void process(int i, Class<?> param, String name, String offsetName) {
        switch (vectorType) {
          case NIOBUFFER:
            Class<?> nioClass = nioBufferClass(param);
            param = nioClass != null ? nioClass : param;
            break;
          case POINTER_AS_LONG:
            // XXX only working for pointers to direct buffers atm
            param = nioBufferClass(param) != null ? Long.TYPE : param;
            break;
          case ARRAY:
            // fall-through: leave it as is.
          default:
            break;
        }

        types.add(param.getCanonicalName());
      }
    });
    return types;
  }

  protected interface ParameterCallback {
    void process(int i, Class<?> param, String name, @Nullable String offsetName);
  }

  /**
   * Calls the callback with every parameter of the method, skipping out the offset parameter
   * introduced by F2J for array arguments.
   *
   * @param method
   * @param callback
   */
  protected void iterateRelevantParameters(Method method, boolean offsets, ParameterCallback callback) {
    if (method.getParameterTypes().length == 0)
      return;

    String[] names = new String[0];
    try {
      names = paranamer.lookupParameterNames(method, true);
    } catch (ParameterNamesNotFoundException e) {
      getLog().warn(e);
    }

    for (int i = 0; i < method.getParameterTypes().length; i++) {
      Class<?> param = method.getParameterTypes()[i];
      if (i > 0 && !offsets && param == Integer.TYPE && method.getParameterTypes()[i - 1].isArray()) {
        continue;
      }
      String name;
      if (names.length > 0)
        name = names[i];
      else
        name = "arg" + i;

      String offsetName = null;
      if (i < method.getParameterTypes().length - 1
          && param.isArray()
          && method.getParameterTypes()[i + 1] == Integer.TYPE)
      offsetName = names[i+1];

      callback.process(i, param, name, offsetName);
    }
  }

  protected Class<?> nioBufferClass(Class<?> clazz) {
      Class<?> compType = clazz.getComponentType();

      if (compType == null) {
          return null;
      } else if (compType.equals(Character.TYPE)) {
          return CharBuffer.class;
      } else if (compType.equals(Integer.TYPE)) {
          return IntBuffer.class;
      } else if (compType.equals(Double.TYPE)) {
          return DoubleBuffer.class;
      } else if (compType.equals(Float.TYPE)) {
          return FloatBuffer.class;
      } else if (compType.equals(Long.TYPE)) {
          return LongBuffer.class;
      } else if (compType.equals(Short.TYPE)) {
          return ShortBuffer.class;
      // there's no boolean[]-from-ByteBuffer thing, so we're stuck with
      // boolean[]
      //} else if (compType.equals(Boolean.TYPE)) {
      //    return ByteBuffer.class;
      } else {
          return null;
      }
  }

  public boolean hasOffsets(Method method) {
    Class<?> last = null;
    for (int i = 0; i < method.getParameterTypes().length; i++) {
      Class<?> param = method.getParameterTypes()[i];
      if (last != null && last.isArray() && param.equals(Integer.TYPE))
        return true;
      last = param;
    }
    return false;
  }

  abstract protected String renderMethod(Method method, boolean offsets, VectorParamOutputVariant v) throws IOException;

  protected Collection<String> renderMethods(Method method, boolean offsets) throws IOException {
    Collection<String> methods = Sets.newHashSet();

    for (VectorParamOutputVariant v : vectorParamOutputVariants()) {
      methods.add(renderMethod(method, offsets, v));
    }

    return methods;
  }
}
