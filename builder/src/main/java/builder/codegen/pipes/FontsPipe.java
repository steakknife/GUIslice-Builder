/**
 *
 * The MIT License
 *
 * Copyright 2018-2020 Paul Conti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
package builder.codegen.pipes;

import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import builder.codegen.CodeGenerator;
import builder.codegen.CodeUtils;
import builder.codegen.Tags;
import builder.codegen.TemplateManager;
import builder.common.FontItem;
import builder.controller.Controller;
import builder.common.EnumFactory;
import builder.common.FontFactory;
import builder.models.KeyPadModel;
import builder.models.KeyPadTextModel;
import builder.models.ProjectModel;
import builder.models.WidgetModel;
import builder.prefs.AlphaKeyPadEditor;
import builder.prefs.NumKeyPadEditor;

/**
 * The Class FontsPipe handles code generation
 * within the "Fonts" tag of our source code.
 * 
 * This section writes out include files or #defines 
 * for fonts used in this project.
 * 
 * @author Paul Conti
 * 
 */
public class FontsPipe extends WorkFlowPipe {

  /** The Constants for templates. */
  private final static String FONT_ADAFRUIT_TEMPLATE          = "<FONT_ADAFRUIT>"; 
  private final static String FONT_ADAFRUIT_TFT_ESPI_TEMPLATE = "<FONT_ADAFRUIT_AND_TFT_ESPI>"; 
  private final static String FONT_TFT_ESPI_TEMPLATE          = "<FONT_TFT_ESPI>"; 
  private final static String FONT_DEFINE_TEMPLATE            = "<FONT_DEFINE>"; 
  private final static String FONT_INCLUDE_TEMPLATE           = "<FONT_INCLUDE>"; 
   
  /** The Constants for macros. */
  private final static String DEFINE_FILE_MACRO      = "DEFINE_FILE";
  private final static String FONT_REF_MACRO         = "FONT_REF";
  private final static String INCLUDE_FILE_MACRO     = "INCLUDE_FILE";
  
  /** The template manager. */
  TemplateManager tm = null;
  
  /**
   * Instantiates a new pipe.
   *
   * @param cg
   *          the cg
   */
  public FontsPipe(CodeGenerator cg) {
    this.cg = cg;
    this.MY_TAG = Tags.TAG_PREFIX+Tags.FONTS_TAG+Tags.TAG_SUFFIX_START;
    this.MY_END_TAG = Tags.TAG_PREFIX+Tags.FONTS_TAG+Tags.TAG_SUFFIX_END;
  }
  
  /**
   * doCodeGen
   *
   * @see builder.codegen.pipes.WorkFlowPipe#doCodeGen(java.lang.StringBuilder)
   */
  public void doCodeGen(StringBuilder sBd) {
    // setup
    FontFactory ff = FontFactory.getInstance();
    
    // scan thru all of the projects widgets and
    // build up a list of all font display names.
    List<String> fontNames = new ArrayList<String>();
    String name = null;
    boolean bAddNumKeyPad = false;
    boolean bAddAlphaKeyPad = false;
    for (WidgetModel m : cg.getModels()) {
      name = m.getFontDisplayName();
      if (name != null)
        fontNames.add(name);
      if (m.getType().equals(EnumFactory.NUMINPUT)) {
        bAddNumKeyPad = true;
      }
      if (m.getType().equals(EnumFactory.TEXTINPUT)) {
        bAddAlphaKeyPad = true;
      }
    }
    // End with keyboard fonts - bug 144 missing keyboard font #include
    // place any keypads at end
    if (bAddNumKeyPad) {
      KeyPadModel m = (KeyPadModel)NumKeyPadEditor.getInstance().getModel();
      name = m.getFontDisplayName();
      if (name != null)
        fontNames.add(name);
    }
    if (bAddAlphaKeyPad) {
      KeyPadTextModel m = (KeyPadTextModel)AlphaKeyPadEditor.getInstance().getModel();
      name = m.getFontDisplayName();
      if (name != null)
        fontNames.add(name);
    }
    if (fontNames.size() == 0)
      return;
    // sort the names and remove duplicates
    CodeUtils.sortListandRemoveDups(fontNames);
    
    // now use our reduced fontNames list to build up a   
    // list of font items for fonts in use by this project.
    List<FontItem> fonts = new ArrayList<FontItem>();
    for (String s : fontNames) {
      fonts.add(ff.getFontItem(s));
    }

    // do we need to output AdaFruit's include file?
    tm = cg.getTemplateManager();
    // do we need to output AdaFruit's include file?
    if (Controller.getTargetPlatform().equals(ProjectModel.PLATFORM_ARDUINO)) {
      // need to output AdaFruit include
      for (FontItem f : fonts) {
        if (!f.getIncludeFile().equals("NULL")) {
          // This code only affects arduino implementation.
          List<String> adafruitTemplate = tm.loadTemplate(FONT_ADAFRUIT_TEMPLATE);
          tm.codeWriter(sBd, adafruitTemplate);
          break;
        }
      }
    }

    // do we need to output TFT_eSPI include file?
    boolean bTFT_ESPI_NEEDED = false;
    boolean bADAFRUIT_TFT_ESPI_NEEDED = false;
    if (Controller.getTargetPlatform().equals(ProjectModel.PLATFORM_TFT_ESPI)) {
      /*   Rules for TFT_eSPI mode:
       * - If no fonts used: No include required
       * - If "built-in" / default font used: No include required
       * - If custom freefonts used: include <TFT_eSPI.h> only
       *   since they're already inside TFT_eSPI.h
       * - If custom not freefonts used: include Adafruit Font/xxx.h only
       *   since they're already inside TFT_eSPI.h
       */
      for (FontItem f : fonts) {
        // A built-in font is indicated with a literal string of "NULL"
        if (!f.getIncludeFile().equals("NULL")) {
          if (f.getName().startsWith("FreeFont")) {
            bTFT_ESPI_NEEDED = true;
          } else {
            bADAFRUIT_TFT_ESPI_NEEDED = true;
          }
        }
      }
    }
    if (bTFT_ESPI_NEEDED || bADAFRUIT_TFT_ESPI_NEEDED) {
      List<String> tft_espiTemplate = tm.loadTemplate(FONT_TFT_ESPI_TEMPLATE);
      tm.codeWriter(sBd, tft_espiTemplate);
    }
    if (bADAFRUIT_TFT_ESPI_NEEDED) {
      List<String> tft_espiTemplate = tm.loadTemplate(FONT_ADAFRUIT_TFT_ESPI_TEMPLATE);
      tm.codeWriter(sBd, tft_espiTemplate);
    }
    // we are ready to output our font information
    tm = cg.getTemplateManager();
    List<String> includeTemplate = null;
    List<String> defineTemplate = null;
    List<String> outputLines = null;
    Map<String, String> map = new HashMap<String,String>();

    for (FontItem f : fonts) {
      if (!f.getIncludeFile().equals("NULL") &&
          Controller.getTargetPlatform().equals(ProjectModel.PLATFORM_TFT_ESPI)) {
        if (!f.getName().startsWith("FreeFont")) {
          // This code only affects arduino implementation.
          includeTemplate = tm.loadTemplate(FONT_INCLUDE_TEMPLATE);;
          map.put(INCLUDE_FILE_MACRO, f.getIncludeFile());
          outputLines = tm.expandMacros(includeTemplate, map);
          tm.codeWriter(sBd, outputLines);
        }
      } else if (!f.getIncludeFile().equals("NULL") && 
          Controller.getTargetPlatform().equals(ProjectModel.PLATFORM_ARDUINO)) {
          // This code only affects arduino implementation.
          includeTemplate = tm.loadTemplate(FONT_INCLUDE_TEMPLATE);;
          map.put(INCLUDE_FILE_MACRO, f.getIncludeFile());
          outputLines = tm.expandMacros(includeTemplate, map);
          tm.codeWriter(sBd, outputLines);
      } else if (!f.getDefineFile().equals("NULL") && 
          Controller.getTargetPlatform().equals(ProjectModel.PLATFORM_LINUX)) {
        // This code only affects linux implementation.
        defineTemplate = tm.loadTemplate(FONT_DEFINE_TEMPLATE);;
        map.put(FONT_REF_MACRO, f.getFontRef());
        map.put(DEFINE_FILE_MACRO, f.getDefineFile());
        outputLines = tm.expandMacros(defineTemplate, map);
        tm.codeWriter(sBd, outputLines);
      }
    }
    
  }

}
  