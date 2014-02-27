/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.util.data;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class Data implements Serializable, Comparable<Data> {

  private static final long serialVersionUID = -2470483891384378865L;

  public final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private Serializable value;

  private DataType type;

  public Data(DataType type) {
    this.type = type;
  }

  public Data(DataType type, Serializable value) {
    setTypeAndValue(type, value);
  }

  public void setTypeAndValue(DataType type, Serializable value) {
    this.type = type;
    setValue(value);
  }

  public DataType getType() {
    return type;
  }

  @SuppressWarnings("unchecked")
  public <T> T getValue() {
    return (T) value;
  }

  @SuppressWarnings("OverlyLongMethod")
  public void setValue(Serializable value) {
    if(value != null) {
      Class<?> valueClass = value.getClass();

      switch(type) {
      case BOOLEAN:
        if(!valueClass.isAssignableFrom(Boolean.class)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      case DATE:
        if(!(value instanceof Date)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      case DECIMAL:
        if(!valueClass.isAssignableFrom(Double.class) && !valueClass.isAssignableFrom(Float.class)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      case INTEGER:
        if(!valueClass.isAssignableFrom(Long.class) && !valueClass.isAssignableFrom(Integer.class)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      case TEXT:
        if(!valueClass.isAssignableFrom(String.class)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      case DATA:
        if(!valueClass.isAssignableFrom(byte[].class)) {
          throw new IllegalArgumentException("DataType " + type + " expected, " + valueClass.getName() + " received.");
        }
        break;

      default:
        break;
      }
    }
    this.value = value;
  }

  public String getValueAsString() {
    switch(type) {
      case DATA:
        return "binary: " + (value == null ? 0 : ((byte[]) value).length) + " bytes";
      case DATE:
        return value == null ? null : DATE_FORMAT.format((Date) value);
      default:
        return value == null ? null : value.toString();
    }
  }

  @Override
  public String toString() {
    return "[" + type + ":" + getValueAsString() + "]";
  }

  @Override
  public int hashCode() {
    int hashCode = 0;

    if(value != null) {
      if(type == DataType.DATA) {
        byte[] bytes = (byte[]) value;
        int byteCount = bytes.length;
        hashCode = byteCount == 0 ? Integer.MAX_VALUE : (byteCount + bytes[0] + bytes[byteCount - 1]) % 17;
      } else {
        hashCode = value.hashCode();
      }
    }

    return hashCode;
  }

  @Override
  public boolean equals(Object otherObj) {
    boolean isEqual = false;

    if(otherObj instanceof Data) {
      Data otherData = (Data) otherObj;

      if(otherData.getType() == type) {
        if(value != null) {
          switch(type) {
          case BOOLEAN:
          case INTEGER:
          case DECIMAL:
          case DATE:
          case TEXT:
            isEqual = value.equals(otherData.getValue());
            break;
          case DATA:
            isEqual = Arrays.equals((byte[]) value, (byte[]) otherData.getValue());
            break;
          }
        } else {
          isEqual = otherData.getValue() == null;
        }
      }
    }

    return isEqual;
  }

  @SuppressWarnings({ "incomplete-switch", "OverlyLongMethod" })
  @Override
  public int compareTo(Data data) {
    int result = -1;

    if(data != null) {
      if(value != null && data.getValue() != null) {
        if(data.getType().isNumberType() && type.isNumberType()) {
          // compare as numbers
          Number numberValue = (Number) value;
          Number dataValue = data.getValue();
          result = Double.compare(numberValue.doubleValue(), dataValue.doubleValue());
        } else if(data.getType() == type) {
          switch(type) {
          case BOOLEAN:
            result = ((Boolean) value).compareTo((Boolean) data.getValue());
            break;
          case DATE:
            result = ((Date) value).compareTo((Date) data.getValue());
            break;
          case TEXT:
            result = ((String) value).compareTo((String) data.getValue());
            break;
          case DATA:
            result = Arrays.equals((byte[]) value, (byte[]) data.getValue()) ? 0 : 1;
            break;
          }
        }
      } else if(value == null && data.getValue() == null) {
        result = 0;
      }
    }

    return result;
  }

}
