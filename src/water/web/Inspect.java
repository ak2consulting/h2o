package water.web;
import java.io.IOException;
import java.util.Properties;
import water.*;
import water.csv.CSVParser.*;
import water.csv.CSVParserKV;

/**
 *
 * @author cliffc@
 */
public class Inspect extends H2OPage {
  
  public Inspect() {
    // No thanks on the refresh, it's hard to use.
    //_refresh = 5;
  }
  
  @Override protected String serve_impl(Properties args) {
    String key_s = args.getProperty("Key");
    Key key = null;
    try { 
      key = Key.make(key_s);      // Get a Key from a raw byte array, if any
    } catch( IllegalArgumentException e ) {
      return H2OPage.wrap(H2OPage.error("Not a valid key: "+ key_s));
    }
    if (!key.user_allowed())
      return H2OPage.wrap(H2OPage.error("Not a user key: "+ key_s));
    // Distributed get
    Value val = DKV.get(key);

    if( val == null )
      return H2OPage.wrap(H2OPage.error("Key not found: "+ key_s));

    if( val instanceof ValueArray &&
        ((ValueArray)val).num_cols() > 0 )
      return structured_array(key,(ValueArray)val);

    RString response = new RString(html);

    formatKeyRow(key,val,response);

    // Dump out the Key
    String ks = key.toString();
    response.replace("keyHref",urlEncode(new String(key._kb)));
    response.replace("key",ks);
    response.replace("ktr",urlEncode(ks));

    // ASCII file?  Give option to do a binary parse
    if( !(val instanceof ValueArray) || ((ValueArray)val).num_cols() == 0 ) {
      String p_key = key_s;
      int idx = key_s.lastIndexOf('.');
      if( idx != -1 )
        p_key = key_s.substring(0,idx);
      p_key += ".dat";
      if( p_key.equals(key_s) ) p_key += "2";
      String s;
      if( DKV.get(Key.make(p_key)) == null ) {
        s = html_parse.replace("%keyHref",urlEncode(key_s));
        s = s.replace("%parsekey",urlEncode(p_key));
        s = s.replace("%pfunc","Parse");
      } else {
        s = html_parse.replace("%keyHref",urlEncode(p_key));
        s = s.replace("%parsekey","");
        s = s.replace("%pfunc","Inspect");
      }
      response.replace("parse",s);
    }

    return response.toString();
  }

  private void formatKeyRow(Key key, Value val, RString response) {
    RString row = response.restartGroup("tableRow");

    // Now the first 100 bytes of Value as a String
    byte[] b = new byte[100];   // Amount to read
    int len=0;
    try {
      len = val.openStream().read(b); // Read, which might force loading.
    } catch( IOException e ) {}
    StringBuilder sb = new StringBuilder();
    for( int i=0; i<len; i++ ) {
      byte c = b[i];
      if( c == '&' ) sb.append("&amp;");
      else if( c == '<' ) sb.append("&lt;");
      else if( c == '>' ) sb.append("&gt;");
      else if( c == '\n' ) sb.append("<br>");
      else if( c == ',' && i+1<len && b[i+1]!=' ' )
        sb.append(", ");
      else sb.append((char)c);
    }
    if( val.length() > len ) sb.append("...");
    row.replace("value",sb);
    row.replace("size",val.length());

    // Do an initial parse of the 1st meg of the dataset
    try {
      float[] fs = new float[100]; // First few columns only
      CSVParserKV.ParserSetup setup = new CSVParserKV.ParserSetup();
      setup.whiteSpaceSeparator = true;
      setup.collapseWhiteSpaceSeparators = true;
      CSVParserKV<float[]> csv = new CSVParserKV<float[]>(key,1,fs,null, setup);
      float sums[] = new float[fs.length];
      float mins[] = new float[fs.length];
      float maxs[] = new float[fs.length];
      for( int i=0; i<fs.length; i++ ) {
        mins[i] = Float.MAX_VALUE;
        maxs[i] = Float.MIN_VALUE;
      }
      int rows = 0;
      int cols = 0;
      for( float[] fs2 : csv ) {
        rows++;
        for( int i=0; i<fs2.length; i++ ) {
          if( Float.isNaN(fs2[i]) )
            break;
          // Skipping any 1st record, try to count columns in the 2nd record
          if( (rows == 2) && i+1>cols ) cols = i+1;
          sums[i] += fs2[i];
          if( fs2[i] < mins[i] ) mins[i] = fs2[i];
          if( fs2[i] > maxs[i] ) maxs[i] = fs2[i];
        }
      }
      // Inject into the HTML
      if( cols > 0 && rows > 0 ) {
        row.replace("rows",rows);
        row.replace("cols",cols);
        for( int i=0; i<Math.min(cols,5); i++ ) {
          String s = String.format("%4.1f / %4.1f / %4.1f",mins[i],sums[i]/rows,maxs[i]);
          row.replace("col"+i,s);
        }
      }
    } catch( SecurityException se ) {
      System.out.println("SecurityException thrown");
    } catch( IllegalArgumentException iae ) {
      System.out.println("IllegalArgumentException thrown");
    }

    row.append();
  }

  final static String html =
      "<h1><a style='%delBtnStyle' href='RemoveAck?Key=%ktr'><button class='btn btn-danger btn-mini'>X</button></a>&nbsp;&nbsp;<a href='/Get?Key=%keyHref'>%key</a>%execbtn</h1>"
    + "<table class='table table-striped table-bordered table-condensed'>"
    + "<colgroup><col/><col/><col/><col/><col colspan=5 align=center/></colgroup>\n"
    + "<thead><tr><th>    <th>    <th>    <th align=center colspan=5>Min / Average / Max <th>   </tr>\n"
    + "       <tr><th>Size<th>Rows<th>Cols<th>Col 0<th>Col 1<th>Col 2<th>Col 3<th>Col 4<th>Value</tr></thead>\n"
    + "<tbody>\n"
    + "%tableRow{\n"
    + "  <tr>"
    + "    <td>%size</td>"
    + "    <td>%rows</td>"
    + "    <td>%cols</td>"
    + "    <td>%col0</td>"
    + "    <td>%col1</td>"
    + "    <td>%col2</td>"
    + "    <td>%col3</td>"
    + "    <td>%col4</td>"
    + "    <td>%value</td>"
    + "  </tr>\n"
    + "}\n"
    + "</tbody>\n"
    + "</table>\n"
    + "%parse";

  final static String html_parse =
    "<a href='/%pfunc?Key=%keyHref&Key2=%parsekey'>Basic Text-File Parse into %parsekey</a>\n";

  // ---------------------
  // Structured Array / Dataset display

  String structured_array( Key key, ValueArray ary ) {
    RString response = new RString(html_ary);
    // Pretty-print the key
    String ks = key.toString();
    response.replace("keyHref",urlEncode(new String(key._kb)));
    response.replace("key",ks);
    response.replace("ktr",urlEncode(ks));
    response.replace("size",ary.length());
    response.replace("rows",ary.num_rows());
    response.replace("rowsize",ary.row_size());
    Key pkey = ary.prior_key();
    response.replace("priorkey",pkey);
    response.replace("priorkeyHref",urlEncode(new String(pkey._kb)));
    response.replace("xform",ary.xform());

    // Header row
    StringBuilder sb = new StringBuilder();
    int num_col = ary.num_cols();
    for( int i=0; i<num_col; i++ )
      sb.append("<th>"+i);
    response.replace("head_row",sb);

    // Data layout scheme
    sb = new StringBuilder();
    for( int i=0; i<num_col; i++ )
      sb.append("<td> +").append(ary.col_off(i)).append("</td>");
    response.replace("offset_row",sb);
    
    sb = new StringBuilder();
    for( int i=0; i<num_col; i++ )
      sb.append("<td>").append(Math.abs(ary.col_size(i))).append("b</td>");
    response.replace("size_row",sb);

    // Compression/math function: Ax+B
    sb = new StringBuilder();
    for( int i=0; i<num_col; i++ ) {
      sb.append("<td>(X");
      int base = ary.col_base(i);
      if( base != 0 ) {
        if( base > 0 ) sb.append('+');
        sb.append(base);
      }
      sb.append(")");
      int sz = ary.col_size(i);
      if( sz == 1 || sz == 2 ) {
        int s = ary.col_scale(i);
        if( s != 1.0 ) sb.append("/").append(s);
      }
      sb.append("</td>");
    }
    response.replace("math_row",sb);

    // Min & max
    sb = new StringBuilder();
    for( int i=0; i<num_col; i++ ) {
      sb.append("<td>");
      double min = ary.col_min(i);
      if( ary.col_size(i) > 0 && ary.col_scale(i) == 1 ) sb.append((long)min); else sb.append(min);
      sb.append(" - ");
      double max = ary.col_max(i);
      if( ary.col_size(i) > 0 && ary.col_scale(i) == 1 ) sb.append((long)max); else sb.append(max);
      sb.append("</td>");
    }
    response.replace("min_max_row",sb);

    // If we have more than 7 rows, display the first & last 3 rows, else
    // display all the rows.
    long num_rows = ary.num_rows();
    if( num_rows > 7 ) {
      display_row(ary,0,response);
      display_row(ary,1,response);
      display_row(ary,2,response);
      display_row(ary,-1,response); // Placeholder view
      display_row(ary,num_rows-3,response);
      display_row(ary,num_rows-2,response);
      display_row(ary,num_rows-1,response);
    } else {
      for( int i=0; i<num_rows; i++ )
        display_row(ary,i,response);
    }

    return response.toString();
  }

  static private void display_row(ValueArray ary, long r, RString response) {
    RString row = response.restartGroup("tableRow");
    int num_col = ary.num_cols();
    StringBuilder sb = new StringBuilder();
    sb.append("<td>Row ").append(r==-1 ? "..." : r).append("</td>");
    for( int i=0; i<num_col; i++ ) {
      sb.append("<td>");
      if( r == -1 ) sb.append("...");
      else {
        if( ary.col_size(i) > 0 && ary.col_scale(i) == 1 )
          sb.append(ary.data (r,i)); // int/long
        else 
          sb.append(ary.datad(r,i)); // float/double
      }
      sb.append("</td>");
    }
    row.replace("data_row",sb);
    row.append();
  }

  final static String html_ary =
      "<h1><a style='%delBtnStyle' href='RemoveAck?Key=%ktr'><button class='btn btn-danger btn-mini'>X</button></a>&nbsp;&nbsp;<a href='/Get?Key=%keyHref'>%key</a>%execbtn</h1>"
    + "<p>Generated from <a href=/Inspect?Key=%priorkeyHref>%priorkey</a> by '%xform'<p>"
    + "%rowsize Bytes-per-row * %rows Rows = Totalsize %size<br>"
    + "<table class='table table-striped table-bordered table-condensed'>"
    + "<thead><tr><th>Column %head_row</tr></thead>\n"
    + "<tbody>\n"
    + "  <tr><td>Record offset</td>%offset_row</tr>\n"
    + "  <tr><td>Column bytes</td>%size_row</tr>\n"
    + "  <tr><td>Internal scaling</td>%math_row</tr>\n"
    + "  <tr><td>Min/Max</td>%min_max_row</tr>\n"
    + "%tableRow{\n"
    + "  <tr>%data_row</tr>\n"
    + "}\n"
    + "</tbody>\n"
    + "</table>\n";
}