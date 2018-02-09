---
title: Code Style
nav_hide: true
layout: default
---

{% include development-header.html %}

{% include documentation-header.html %}

# Code Style Guide

## Java Structures & Naming

### Packages and Imports

Package names are lower case.

Imports should be organised alphabetically by top level package, e.g.

```
import android.os.Intent

import com.fsck.k9.activity.LoginActivity

import java.io.InputStream
import java.io.OutputStream
```

### Classes

The number of classes per file should be minimised. Lambda-style single method
anonymous classes are acceptable, but nested private classes should be avoided
where possible as they are generally difficult to test.

Classes are named in first-letter capitalised 'CamelCase'.

Declaring classes `final` is generally not helpful as it makes mocking more difficult.

```
public class A {

}
```

### Methods

Methods should ideally be small enough to fit on a screen (if it is not
consider a Strategy class).

Methods should be named in lower-case 'camelCase'.

Declaring methods `final` is generally not helpful as it makes mocking more difficult.

However they should follow the principle of least privilege - prefer private to
protected, protected to package, package to public.

```
public class A {

  public void doB() {

  }

}
```

Methods that are inherited must be marked with the `@Override` annotation.


### Exceptions

Checked exceptions should be used as a form of expected response where it's
almost certain the caller will be able to respond reasonably.
Passing exceptions up is a code smell.

Wrapping exceptions in RuntimeExceptions is also not good behaviour but may be used where necessary.

In general catch the tightest possible exception - prefer `MyException` to `Exception` and `Exception` to `Throwable`.

Doing nothing is almost never the right behaviour - usually it should be at least logged. In a case where either there is no action or only logging a test should describe the expected behaviour e.g.

```
public void setPort(String port) {
  try {
    port = Integer.parseInt(port)
  } catch (NumberFormatException ignored) {
  }
}

@Test
public void setPort_withNonNumericValue_shouldKeepPreviousValue() {
   a.setPort("invalidValue");
   assertEquals(defaultPort, a.getPort();
}

```

### Statements:

One statement per line - e.g.

```
a++; b++;
```

should be changed to:

```
a++;
b++;
```

## Variables

Variables should be typed as tightly as possible and declared as close to first usage as possible. i.e. prefer

Variable should be named in lower-case 'camelCase'.

Variables should be final where possible and should follow the principle of least privilege.

```
public class A {

  private final C usefulData;

}
```

Static variables should be qualified by the class name, not an instance of the class. e.g.:

```
class B {
  private A a;

  private void doC() {
     A.invokeStaticMethod();
  }
}
```

instead of:

```
class B {
  private A a;

  private void doC() {
     a.invokeStaticMethod();
  }
}
```

### Constants (`final static` variables)

Constants should be written in capitalised 'SNAKE_CASE'. `final static` variables should generally only be of simple types (`String` / `int`) to avoid leaking memory.

## Formatting & Style

### Brackets & Braces

K&R Java style. See examples both here and elsewhere.

Braces are mandatory for if-statements (for one liners consider the ternary operator):

```
if (condition) {

} else if (otherCondition) {

} else {

}
```

### Whitespace & Line Length

Prefer spaces to tabs. Aim for a maximum line length of 120. Multiple nesting indentation suggests the code should be refactored into helper classes / sub methods e.g.:

```
public void doSomething() {
   if (this) {
      for (that) {
          while (another thing) {

          }
      }
   }
}
```

could be rewritten:

```
public void doSomething() {
   if (this) {
      doThisSomething();
   }
}

private void doThisSomething() {
  for (that) {
      while (another thing) {

      }
  }
}
```

### Comments

Code comments are generally unnecessary. Code should be tested such that a
different implementation is either equally valid or would fail a test.

Comments invariably rot, making life less confusing.

Comments that denote that something happens so that something doesn't break are better written as tests.

For example:

```
public void doSomething(A a) {
   a.reset(); //Necessary to avoid IllegalStateException

   a.setTheThing();
}
```

should be changed to:

```
public void doSomething(B b) {
   b.reset(); //Necessary to avoid IllegalStateException

   b.setTheThing();
}

...

@Test
public void doSomething_doesntThrowIllegalStateException() {
   a.doSomething(b);
}
```

or

```
@Test
public void doSomething_resetsBState() {
   a.doSomething(b);

   verify(b.reset());
}
```


Comments that denote sections of a method are a code-smell and indicate the method can be split up into well named helper methods. For example:

```
public void doSomething(A a) {
   //This flanges the flid
   a.flutter();

   b.doSomethingElse();
   c.doAnotherThing();
}
```

is better re-written:

```
public void doSomething(A a) {
   flangeTheFlid(a);
   b.doSomethingElse();
   c.doAnotherThing();
}

private void flangeTheFlid(A a) {
   a.flutter();
}
```

### Javadoc

Javadoc is unnecessary for several reasons:

1. We currently don't publish it anywhere
2. It makes class files longer.
3. Good Javadoc (e.g. the [Android API](https://developer.android.com/reference/android/app/Activity.html) ) is time-consuming to write and bad Javadoc is just a space filler that helps no-one.
4. We are not currently aiming to produce an externally visible API -
there's no end user other than developers of the app itself.

Before we started publishing Javadoc we'd want to formalise the target API. Then the Javadoc itself would probably be best in interface files (reducing the need for Javadoc in files with application code and ensuring a stable API).
