package water.parser;

import java.util.ArrayList;
import java.util.Arrays;

import water.*;
import water.parser.ValueString;

public class CsvParser extends CustomParser {

  public final byte CHAR_DECIMAL_SEPARATOR;
  public final byte CHAR_SEPARATOR;

  private static final byte SKIP_LINE = 0;
  private static final byte EXPECT_COND_LF = 1;
  private static final byte EOL = 2;
  private static final byte TOKEN = 3;
  private static final byte COND_QUOTED_TOKEN = 4;
  private static final byte NUMBER = 5;
  private static final byte NUMBER_SKIP = 6;
  private static final byte NUMBER_SKIP_NO_DOT = 7;
  private static final byte NUMBER_FRACTION = 8;
  private static final byte NUMBER_EXP = 9;
  private static final byte NUMBER_EXP_NEGATIVE = 10;
  private static final byte NUMBER_EXP_START = 11;
  private static final byte NUMBER_END = 12;
  private static final byte STRING = 13;
  private static final byte COND_QUOTE = 14;
  private static final byte SEPARATOR_OR_EOL = 15;
  private static final byte WHITESPACE_BEFORE_TOKEN = 16;
  private static final byte STRING_END = 17;
  private static final byte COND_QUOTED_NUMBER_END = 18;
  private static final byte POSSIBLE_EMPTY_LINE = 19;
  private static final byte POSSIBLE_CURRENCY = 20;

  private static final long LARGEST_DIGIT_NUMBER = 1000000000000000000L;

  public final Key _aryKey;

  public final int _numColumns;

  public final boolean _skipFirstLine;

  DParseTask callback;


  public CsvParser(Key aryKey, int numColumns, byte separator, byte decimalSeparator, DParseTask callback, boolean skipFirstLine) {
    _aryKey = aryKey;
    _numColumns = numColumns;
    CHAR_SEPARATOR = separator;
    CHAR_DECIMAL_SEPARATOR = decimalSeparator;
    this.callback = callback;
    _skipFirstLine = skipFirstLine;
  }

  @SuppressWarnings("fallthrough")
  @Override public final void parse(Key key) {
    ValueArray _ary = _aryKey == null ? null : (ValueArray)DKV.get(_aryKey).get();
    ValueString _str = new ValueString();
    byte[] bits = DKV.get(key).memOrLoad();
    int offset = 0;
    int state = _skipFirstLine ? SKIP_LINE : WHITESPACE_BEFORE_TOKEN;
    int quotes = 0;
    long number = 0;
    int exp = 0;
    boolean decimal = false;
    int fractionDigits = 0;
    int numStart = 0;
    int tokenStart = 0; // used for numeric token to backtrace if not successful
    int secondChunk = 0; // 0 = not, 1 = in, or back in first one, 2 == no luck
    int colIdx = 0;
    byte c = bits[offset];
    // skip comments for the first chunk
    if ((_ary == null) || (ValueArray.getChunkIndex(key) == 0)) {
      while (c == '#' || c == '@'/*also treat as comments leading '@' from ARFF format*/) {
        while ((offset   < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset  ] != CHAR_LF)) ++offset;
        if    ((offset+1 < bits.length) && (bits[offset] == CHAR_CR) && (bits[offset+1] == CHAR_LF)) ++offset;
        ++offset;
        if (offset >= bits.length)
          return;
        c = bits[offset];
      }
    }
    callback.newLine();
MAIN_LOOP:
    while (true) {
NEXT_CHAR:

      switch (state) {
        // ---------------------------------------------------------------------
        case SKIP_LINE:
          if (isEOL(c)) {
            state = EOL;
          } else {
            break NEXT_CHAR;
          }
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case EXPECT_COND_LF:
          state = POSSIBLE_EMPTY_LINE;
          if (c == CHAR_LF)
            break NEXT_CHAR;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case STRING:
          if (c == quotes) {
            state = COND_QUOTE;
            break NEXT_CHAR;
          }
          if ((quotes != 0) || ((!isEOL(c) && (c != CHAR_SEPARATOR)))) {
            _str.addChar();
            break NEXT_CHAR;
          }
          // fallthrough to STRING_END
        // ---------------------------------------------------------------------
        case STRING_END:
          if ((c != CHAR_SEPARATOR) && (c == CHAR_SPACE))
            break NEXT_CHAR;
          // we have parsed the string enum correctly
          if((_str._off + _str._length) > _str._buf.length){ // crossing chunk boundary
            assert _str._buf != bits;
            _str.addBuff(bits);
          }
          callback.addStrCol(colIdx, _str);
          _str.set(null, 0, 0);
          ++colIdx;
          state = SEPARATOR_OR_EOL;
          // fallthrough to SEPARATOR_OR_EOL
        // ---------------------------------------------------------------------
        case SEPARATOR_OR_EOL:
          if (c == CHAR_SEPARATOR) {
            state = WHITESPACE_BEFORE_TOKEN;
            break NEXT_CHAR;
          }
          if (c==CHAR_SPACE)
            break NEXT_CHAR;
          // fallthrough to EOL
        // ---------------------------------------------------------------------
        case EOL:
          if (colIdx != 0) {
            colIdx = 0;
            callback.newLine();
          }
          state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
          if (secondChunk != 0)
            break MAIN_LOOP; // second chunk only does the first row
          break NEXT_CHAR;
        // ---------------------------------------------------------------------
        case POSSIBLE_CURRENCY:
          if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEPARATOR) || (c == '+')) {
            state = TOKEN;
          } else {
            _str.set(bits,offset-1,0);
            _str.addChar();
            if (c == quotes) {
              state = COND_QUOTE;
              break NEXT_CHAR;
            }
            if ((quotes != 0) || ((!isEOL(c) && (c != CHAR_SEPARATOR)))) {
              state = STRING;
            } else {
              state = STRING_END;
            }
          }
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case POSSIBLE_EMPTY_LINE:
          if (isEOL(c)) {
            if (c == CHAR_CR)
              state = EXPECT_COND_LF;
            break NEXT_CHAR;
          }
          state = WHITESPACE_BEFORE_TOKEN;
          // fallthrough to WHITESPACE_BEFORE_TOKEN
        // ---------------------------------------------------------------------
        case WHITESPACE_BEFORE_TOKEN:
          if (c == CHAR_SPACE) {
              break NEXT_CHAR;
          } else if (c == CHAR_SEPARATOR) {
            // we have empty token, store as NaN
            callback.addInvalidCol(colIdx);
            ++colIdx;
            break NEXT_CHAR;
          } else if (isEOL(c)) {
            callback.addInvalidCol(colIdx);
            state = EOL;
            continue MAIN_LOOP;
          }
          // fallthrough to COND_QUOTED_TOKEN
        // ---------------------------------------------------------------------
        case COND_QUOTED_TOKEN:
          state = TOKEN;
          if ((c == CHAR_SINGLE_QUOTE) || (c == CHAR_DOUBLE_QUOTE)) {
            assert (quotes == 0);
            quotes = c;
            break NEXT_CHAR;
          }
          // fallthrough to TOKEN
        // ---------------------------------------------------------------------
        case TOKEN:
          if( callback.isString(colIdx) ) { // Forced already to a string col?
            state = STRING; // Do not attempt a number parse, just do a string parse
            _str.set(bits, offset, 0);
            continue MAIN_LOOP;
          } else if (((c >= '0') && (c <= '9')) || (c == '-') || (c == CHAR_DECIMAL_SEPARATOR) || (c == '+')) {
            state = NUMBER;
            number = 0;
            fractionDigits = 0;
            decimal = false;
            numStart = offset;
            tokenStart = offset;
            if (c == '-') {
              exp = -1;
              ++numStart;
              break NEXT_CHAR;
            } else if(c == '+'){
              exp = 1;
              ++numStart;
              break NEXT_CHAR;
            } else {
              exp = 1;
            }
            // fallthrough
          } else if (c == '$') {
            state = POSSIBLE_CURRENCY;
            break NEXT_CHAR;
          } else {
            state = STRING;
            _str.set(bits, offset, 0);
            continue MAIN_LOOP;
          }
          // fallthrough to NUMBER
        // ---------------------------------------------------------------------
        case NUMBER:
          if ((c >= '0') && (c <= '9')) {
            number = (number*10)+(c-'0');
            if (number >= LARGEST_DIGIT_NUMBER)
              state = NUMBER_SKIP;
            break NEXT_CHAR;
          } else if (c == CHAR_DECIMAL_SEPARATOR) {
            ++numStart;
            state = NUMBER_FRACTION;
            fractionDigits = offset;
            decimal = true;
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            ++numStart;
            state = NUMBER_EXP_START;
            break NEXT_CHAR;
          }
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          // fallthrough to COND_QUOTED_NUMBER_END
        // ---------------------------------------------------------------------
        case COND_QUOTED_NUMBER_END:
          if ( c == quotes) {
            state = NUMBER_END;
            quotes = 0;
            break NEXT_CHAR;
          }
          // fallthrough NUMBER_END
        case NUMBER_END:
          if (c == CHAR_SEPARATOR) {
            exp = exp - fractionDigits;
            callback.addNumCol(colIdx,number,exp);
            ++colIdx;
            // do separator state here too
            state = WHITESPACE_BEFORE_TOKEN;
            break NEXT_CHAR;
          } else if (isEOL(c)) {
            exp = exp - fractionDigits;
            callback.addNumCol(colIdx,number,exp);
            // do EOL here for speedup reasons
            colIdx = 0;
            callback.newLine();
            state = (c == CHAR_CR) ? EXPECT_COND_LF : POSSIBLE_EMPTY_LINE;
            if (secondChunk != 0)
              break MAIN_LOOP; // second chunk only does the first row
            break NEXT_CHAR;
          } else if ((c == '%')) {
            state = NUMBER_END;
            exp -= 2;
            break NEXT_CHAR;
          } else if ((c != CHAR_SEPARATOR) && ((c == CHAR_SPACE) || (c == CHAR_TAB))) {
            state = NUMBER_END;
            break NEXT_CHAR;
          } else {
            state = STRING;
            offset = tokenStart-1;
            _str.set(bits,tokenStart,0);
            break NEXT_CHAR; // parse as String token now
          }
        // ---------------------------------------------------------------------
        case NUMBER_SKIP:
          ++numStart;
          if ((c >= '0') && (c <= '9')) {
            break NEXT_CHAR;
          } else if (c == CHAR_DECIMAL_SEPARATOR) {
            state = NUMBER_SKIP_NO_DOT;
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_SKIP_NO_DOT:
          ++numStart;
          if ((c >= '0') && (c <= '9')) {
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            state = NUMBER_EXP_START;
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_FRACTION:
          if ((c >= '0') && (c <= '9')) {
            if (number >= LARGEST_DIGIT_NUMBER) {
              if (decimal)
                fractionDigits = offset - 1 - fractionDigits;
              state = NUMBER_SKIP;
            } else {
              number = (number*10)+(c-'0');
            }
            break NEXT_CHAR;
          } else if ((c == 'e') || (c == 'E')) {
            ++numStart;
            if (decimal)
              fractionDigits = offset - 1 - fractionDigits;
            state = NUMBER_EXP_START;
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          if (decimal)
            fractionDigits = offset - fractionDigits-1;
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_EXP_START:
          if (exp == -1) {
            number = -number;
          }
          exp = 0;
          if (c == '-') {
            ++numStart;
            state = NUMBER_EXP_NEGATIVE;
            break NEXT_CHAR;
          } else {
            state = NUMBER_EXP;
            if (c == '+') {
              ++numStart;
              break NEXT_CHAR;
            }
          }
          // fallthrough to NUMBER_EXP
        // ---------------------------------------------------------------------
        case NUMBER_EXP:
          if ((c >= '0') && (c <= '9')) {
            ++numStart;
            exp = (exp*10)+(c-'0');
            break NEXT_CHAR;
          }
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case NUMBER_EXP_NEGATIVE:
          if ((c >= '0') && (c <= '9')) {
            exp = (exp*10)+(c-'0');
            break NEXT_CHAR;
          }
          exp = - exp;
          state = COND_QUOTED_NUMBER_END;
          continue MAIN_LOOP;
        // ---------------------------------------------------------------------
        case COND_QUOTE:
          if (c == quotes) {
            //TODO
            _str.set(bits, offset+1, 0);
            state = STRING;
            break NEXT_CHAR;
          } else {
            quotes = 0;
            state = STRING_END;
            continue MAIN_LOOP;
          }
        // ---------------------------------------------------------------------
        default:
          assert (false) : " We have wrong state "+state;
      } // end NEXT_CHAR
      ++offset; // do not need to adjust for offset increase here - the offset is set to tokenStart-1!
      if (offset < 0) {
        assert secondChunk != 0 : "This can only happen when we are in second chunk and are reverting to first one.";
        secondChunk = 0;
        Value v = DKV.get(key); // we had the last key
        assert (v != null) : "The value used to be there!";
        bits = v.memOrLoad();
        offset += bits.length;
        _str.set(bits,offset,0);
      } else if (offset >= bits.length) {
        ++secondChunk;
        // if we can't get further we might have been the last one and we must
        // commit the latest guy if we had one.
        if (_ary == null) {
          if ((state != EXPECT_COND_LF) && (state != POSSIBLE_EMPTY_LINE)) {
            c = CHAR_LF;
            continue MAIN_LOOP;
          }
          break MAIN_LOOP;
        }
        numStart -= bits.length;
        if (state == NUMBER_FRACTION)
          fractionDigits -= bits.length;
        offset -= bits.length;
        tokenStart -= bits.length;
        long chkidx = ValueArray.getChunkIndex(key);
        Value v = (secondChunk < 2 && chkidx+1 < _ary.chunks()) 
          ? DKV.get(_ary.getChunkKey(chkidx+1)) : null;
        // if we can't get further we might have been the last one and we must
        // commit the latest guy if we had one.
        if (v == null) {
          if ((state != EXPECT_COND_LF) && (state != POSSIBLE_EMPTY_LINE)) {
            c = CHAR_LF;
            continue MAIN_LOOP;
          }
          break MAIN_LOOP;
        }
        bits = v.memOrLoad();         // Could limit to eg 512 bytes
        if (bits[0] == CHAR_LF && state == EXPECT_COND_LF)
          break MAIN_LOOP; // when the first character we see is a line end
      }
      c = bits[offset];
    } // end MAIN_LOOP
    if (colIdx == 0)
      callback.rollbackLine();
  }

  private static boolean isWhitespace(byte c) {
    return (c == CHAR_SPACE) || (c == CHAR_TAB);
  }

  private static boolean isEOL(byte c) {
    return (c >= CHAR_LF) && ( c<= CHAR_CR);
  }

  /** Setup of the parser.
   *
   * Simply holds the column names, their length also determines the number of
   * columns, the separator used and whether the CSV file had a header or not.
   */
  public static class Setup {
    public final byte _separator;
    public final boolean _header;
    // Row zero is column names.
    // Remaining rows are parsed from the given data, until we run out
    // of data or hit some arbitrary display limit.
    public final String[][] _data;
    public final int _numlines;        // Number of lines parsed
    public final byte[] _bits;  // The original bits
    public Setup(byte separator, boolean header, String[][] data, int numlines, byte[] bits) {
      _separator = separator;
      _header = header;
      _data = data;
      _numlines = numlines;
      _bits = bits;
    }
    @Override public boolean equals( Object o ) {
      if( o == null || !(o instanceof Setup) ) return false;
      Setup s = (Setup)o;
      // "Compatible" setups means same columns and same separators
      return _separator == s._separator && _data[0].length == s._data[0].length;
    }
    @Override public String toString() {
      return "'"+_separator+"' head="+_header+" cols="+_data[0].length;
    }
  }

  /** Separators recognized by the parser.  You can add new separators to this
   *  list and the parser will automatically attempt to recognize them.  In
   *  case of doubt the separators are listed in descending order of
   *  probability, with space being the last one - space must always be the
   *  last one as it is used if all other fails because multiple spaces can be
   *  used as a single separator.
   */
  private static byte[] separators = new byte[] { 1/* '^A',  Hive table column separator */, ',', ';', '|', '\t',  ' '/*space is last in this list, because we allow multiple spaces*/ };

  /** Dermines the number of separators in given line. Correctly handles quoted
   * tokens.
   */
  private static int[] determineSeparatorCounts(String from) {
    int[] result = new int[separators.length];
    byte[] bits = from.getBytes();
    int offset = 0;
  MAIN_LOOP:
    while (offset < bits.length) {
      byte c = bits[offset];
      for (int i = 0; i < separators.length; ++i)
        if (c == separators[i])
          ++result[i];
      if ((c == '"') || (c == '\'')) {
        ++offset;
        while (offset < bits.length) {
          if (bits[offset] == c) {
            if (offset+1 == bits.length) // last character on the line was a quote, we are done
              break MAIN_LOOP;
            if (bits[offset+1] != c)
              break;
          }
          ++offset;
        }
      }
      ++offset;
    }
    return result;
  }

  /** Determines the tokens that are inside a line and returns them as strings
   *  in an array.  Assumes the given separator.
   */
  private static String[] determineTokens(String from, byte separator) {
    ArrayList<String> tokens = new ArrayList();
    byte[] bits = from.getBytes();
    int offset = 0;
    int quotes = 0;
    while (offset < bits.length) {
      while ((offset < bits.length) && (bits[offset] == CHAR_SPACE)) ++offset; // skip first whitespace
      if(offset == bits.length)break;
      StringBuilder t = new StringBuilder();
      byte c = bits[offset];
      if ((c == '"') || (c == '\'')) {
        quotes = c;
        ++offset;
      }
      while (offset < bits.length) {
        c = bits[offset];
        if ((c == quotes)) {
          ++offset;
          if ((offset < bits.length) && (bits[offset] == c)) {
            t.append((char)c);
            ++offset;
            continue;
          }
          quotes = 0;
          break;
        } else if ((quotes == 0) && ((c == separator) || (c == CHAR_CR) || (c == CHAR_LF))) {
          break;
        } else {
          t.append((char)c);
          ++offset;
        }
      }
      c = (offset == bits.length) ? CHAR_LF : bits[offset];
      tokens.add(t.toString());
      if ((c == CHAR_CR) || (c == CHAR_LF) || (offset == bits.length))
        break;
      if (c != separator)
        return new String[0]; // an error
      ++offset;               // Skip separator
    }
    // If we have trailing empty columns (split by seperators) such as ",,\n"
    // then we did not add the final (empty) column, so the column count will
    // be down by 1.  Add an extra empty column here
    if( bits[bits.length-1] == separator  && bits[bits.length-1] != CHAR_SPACE)
      tokens.add("");
    return tokens.toArray(new String[tokens.size()]);
  }

  /** Assumption is no numbers in l1 and at least one number in l2.
   *
   * For simplicity I am using Java's parsing functions.  Header means that all
   * tokens in first line are strings and at least one token in the second line
   * is a number.
   */
  private static Setup guessColumnNames(String[] l1, String[] l2, byte separator, ArrayList<String> lines, int numlines, byte[] bits) {
    boolean hasNumber = false;
    for( String s : l1 ) {
      try {
        Double.parseDouble(s);
        hasNumber = true;       // Number in 1st row guesses: No Column Header
        break;
      } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
    }
    boolean hasHeader = false;
    if (!hasNumber) {
      for( String s : l2 ) {
        try {
          Double.parseDouble(s);
          hasHeader = true; // Has number in 2nd row, so guess: has Column Header
          break;
        } catch (NumberFormatException e) { /*Pass - determining if number is possible*/ }
      }
    }
    
    // Return an array with headers in data[0] and the remaining rows pre-parsed.
    String[][] data = new String[lines.size()+(hasHeader?0:1)][];
    int l=0;
    if( !hasHeader ) {          // Make junky 0,1,2,3,... headers
      data[l++] = new String[l1.length];
      for( int i=0; i<l1.length; i++ ) data[0][i] = Integer.toString(i);
    }
    data[l++] = l1;
    data[l++] = l2;
    int m=2;
    while( m < lines.size() )
      data[l++] = determineTokens(lines.get(m++), separator);
    assert data.length==l : data.length +" "+l+" has="+hasHeader;
    
    return new Setup(separator, hasHeader, data, numlines, bits);
  }

  /** Determines the CSV parser setup from the first two lines.  Also parses
   *  the next few lines, tossing out comments and blank lines.
   *
   *  A separator is selected if both two lines have the same ammount of them
   *  and the tokenization then returns same number of columns.
   */
  public static Setup guessCsvSetup(byte[] bits) {
    ArrayList<String> lines = new ArrayList();
    int offset = 0;
    int numlines = 0;
    while (offset < bits.length ) {
      int lineStart = offset;
      while ((offset < bits.length) && (bits[offset] != CHAR_CR) && (bits[offset] != CHAR_LF)) ++offset;
      int lineEnd = offset;
      ++offset;
      if ((offset < bits.length) && (bits[offset] == CHAR_LF)) ++offset;
      if (bits[lineStart] == '#') continue; // Ignore      comment lines
      if (bits[lineStart] == '@') continue; // Ignore ARFF comment lines
      if (lineEnd>lineStart) {
        numlines++;                           // Estimate data lines in this chunk
        if( lines.size() < 5 )
          lines.add(new String(bits,lineStart, lineEnd-lineStart));
      }
    }
    // we do not have enough lines to decide
    if( lines.size() < 2 ) return new Setup((byte)' ',false,null,0,bits);
    // when we have two lines, calculate the separator counts on them
    int[] s1 = determineSeparatorCounts(lines.get(0));
    int[] s2 = determineSeparatorCounts(lines.get(1));
    // now we have the counts - if both lines have the same number of separators
    // the we assume it is the separator. Separators are ordered by their
    // likelyhoods. If no separators have same counts, space will be used as the
    // default one
    for (int i = 0; i < s1.length; ++i)
      if (((s1[i] == s2[i]) && (s1[i] != 0)) || (i == separators.length-1)) {
        try {
          String[] t1 = determineTokens(lines.get(0), separators[i]);
          String[] t2 = determineTokens(lines.get(1), separators[i]);
          if (t1.length != t2.length)
            continue;
          return guessColumnNames(t1,t2,separators[i],lines,numlines, bits);
        } catch (Exception e) { /*pass; try another parse attempt*/ }
      }
    return new Setup((byte)' ',false,null,0,bits);
  }

  public static Setup inspect(byte[] bits) {
    return guessCsvSetup(bits);
  }
}
