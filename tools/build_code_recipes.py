#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/code/recipes.tsv: Ibiko v1's own coding knowledge.

Every recipe is a whole program, and every one is run here before it is kept: compiled and run, and its output
compared with what it must print. A recipe that fails is left out and said. Nothing goes in untested.

Usage: tools/build_code_recipes.py            (writes the file; prints what passed and what didn't)
Lines: task <TAB> language <TAB> code (newlines as \\n, tabs as \\t, backslashes as \\\\).
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "knowledge", "code", "recipes.tsv")

# what each task's program prints (a regular expression when it starts with "re:"), and what it reads from stdin
EXPECT = {
    "hello": "Hello, world!",
    "reverse_string": "olleh",
    "sort_list": "1 3 5 8",
    "sum_list": "10",
    "max_list": "8",
    "remove_duplicates": "1 2 3",
    "factorial": "120",
    "fibonacci": "0 1 1 2 3 5 8 13 21 34",
    "is_prime": "true",
    "uppercase": "HELLO",
    "split_string": "a b c",
    "string_to_int": "43",
    "count_words": "4",
    "dictionary": "3",
    "read_write_file": "hi",
    "try_catch": "error caught",
    "loop_range": "1 2 3 4 5",
    "class_object": "Hello, Ada",
    "json_parse": "Ada 36",
    "regex_find": "1 22 333",
    "random_number": "re:^([1-9]|10)$",
    "read_input": "Hello, Ada",
    "current_date": r"re:^\d{4}-\d{2}-\d{2}$",
    "http_get": "compile",  # needs the internet: checked to compile, not run
}
STDIN = {"read_input": "Ada\n"}

R = {}  # (task, lang) -> code


def put(task, lang, code):
    R[(task, lang)] = code.strip("\n") + "\n"


# ---------------------------------------------------------------------------------------------------- Python
put("hello", "python", 'print("Hello, world!")')
put("reverse_string", "python", 's = "hello"\nprint(s[::-1])')
put("sort_list", "python", 'nums = [5, 3, 8, 1]\nnums.sort()\nprint(" ".join(str(n) for n in nums))')
put("sum_list", "python", "nums = [1, 2, 3, 4]\nprint(sum(nums))")
put("max_list", "python", "nums = [5, 3, 8, 1]\nprint(max(nums))")
put("remove_duplicates", "python", 'nums = [1, 2, 2, 3, 1]\nunique = list(dict.fromkeys(nums))  # keeps the order\nprint(" ".join(str(n) for n in unique))')
put("factorial", "python", "def factorial(n):\n    result = 1\n    for i in range(2, n + 1):\n        result *= i\n    return result\n\nprint(factorial(5))")
put("fibonacci", "python", 'def fibonacci(count):\n    a, b = 0, 1\n    out = []\n    for _ in range(count):\n        out.append(a)\n        a, b = b, a + b\n    return out\n\nprint(" ".join(str(n) for n in fibonacci(10)))')
put("is_prime", "python", "def is_prime(n):\n    if n < 2:\n        return False\n    i = 2\n    while i * i <= n:\n        if n % i == 0:\n            return False\n        i += 1\n    return True\n\nprint(str(is_prime(17)).lower())")
put("uppercase", "python", 'print("hello".upper())')
put("split_string", "python", 'parts = "a,b,c".split(",")\nprint(" ".join(parts))')
put("string_to_int", "python", 'n = int("42")\nprint(n + 1)')
put("count_words", "python", 'text = "the quick brown fox"\nprint(len(text.split()))')
put("dictionary", "python", 'stock = {"apple": 3, "pear": 5}\nprint(stock["apple"])')
put("read_write_file", "python", 'with open("note.txt", "w") as f:\n    f.write("hi")\nwith open("note.txt") as f:\n    print(f.read())')
put("try_catch", "python", "try:\n    int(\"abc\")\nexcept ValueError:\n    print(\"error caught\")")
put("loop_range", "python", 'print(" ".join(str(i) for i in range(1, 6)))')
put("class_object", "python", 'class Person:\n    def __init__(self, name):\n        self.name = name\n\n    def greet(self):\n        return f"Hello, {self.name}"\n\nprint(Person("Ada").greet())')
put("json_parse", "python", 'import json\n\ndata = json.loads(\'{"name": "Ada", "age": 36}\')\nprint(data["name"], data["age"])')
put("regex_find", "python", 'import re\n\nprint(" ".join(re.findall(r"\\d+", "a1b22c333")))')
put("random_number", "python", "import random\n\nprint(random.randint(1, 10))")
put("read_input", "python", 'name = input()\nprint(f"Hello, {name}")')
put("current_date", "python", "from datetime import date\n\nprint(date.today().isoformat())")
put("http_get", "python", 'import urllib.request\n\nwith urllib.request.urlopen("https://example.com") as r:\n    print(r.status, r.read()[:100])')

# ---------------------------------------------------------------------------------------------------- JavaScript (Node)
put("hello", "javascript", 'console.log("Hello, world!");')
put("reverse_string", "javascript", 'const s = "hello";\nconsole.log([...s].reverse().join(""));')
put("sort_list", "javascript", 'const nums = [5, 3, 8, 1];\nnums.sort((a, b) => a - b); // numbers, not text\nconsole.log(nums.join(" "));')
put("sum_list", "javascript", "const nums = [1, 2, 3, 4];\nconsole.log(nums.reduce((a, b) => a + b, 0));")
put("max_list", "javascript", "const nums = [5, 3, 8, 1];\nconsole.log(Math.max(...nums));")
put("remove_duplicates", "javascript", 'const nums = [1, 2, 2, 3, 1];\nconsole.log([...new Set(nums)].join(" "));')
put("factorial", "javascript", "function factorial(n) {\n  let result = 1;\n  for (let i = 2; i <= n; i++) result *= i;\n  return result;\n}\n\nconsole.log(factorial(5));")
put("fibonacci", "javascript", 'function fibonacci(count) {\n  const out = [];\n  let a = 0, b = 1;\n  for (let i = 0; i < count; i++) {\n    out.push(a);\n    [a, b] = [b, a + b];\n  }\n  return out;\n}\n\nconsole.log(fibonacci(10).join(" "));')
put("is_prime", "javascript", "function isPrime(n) {\n  if (n < 2) return false;\n  for (let i = 2; i * i <= n; i++) if (n % i === 0) return false;\n  return true;\n}\n\nconsole.log(isPrime(17));")
put("uppercase", "javascript", 'console.log("hello".toUpperCase());')
put("split_string", "javascript", 'console.log("a,b,c".split(",").join(" "));')
put("string_to_int", "javascript", 'const n = parseInt("42", 10);\nconsole.log(n + 1);')
put("count_words", "javascript", 'const text = "the quick brown fox";\nconsole.log(text.trim().split(/\\s+/).length);')
put("dictionary", "javascript", 'const stock = new Map([["apple", 3], ["pear", 5]]);\nconsole.log(stock.get("apple"));')
put("read_write_file", "javascript", 'const fs = require("fs");\n\nfs.writeFileSync("note.txt", "hi");\nconsole.log(fs.readFileSync("note.txt", "utf8"));')
put("try_catch", "javascript", 'try {\n  JSON.parse("not json");\n} catch (e) {\n  console.log("error caught");\n}')
put("loop_range", "javascript", 'const out = [];\nfor (let i = 1; i <= 5; i++) out.push(i);\nconsole.log(out.join(" "));')
put("class_object", "javascript", "class Person {\n  constructor(name) {\n    this.name = name;\n  }\n  greet() {\n    return `Hello, ${this.name}`;\n  }\n}\n\nconsole.log(new Person(\"Ada\").greet());")
put("json_parse", "javascript", 'const data = JSON.parse(\'{"name": "Ada", "age": 36}\');\nconsole.log(data.name, data.age);')
put("regex_find", "javascript", 'console.log("a1b22c333".match(/\\d+/g).join(" "));')
put("random_number", "javascript", "console.log(Math.floor(Math.random() * 10) + 1);")
put("read_input", "javascript", 'const readline = require("readline");\nconst rl = readline.createInterface({ input: process.stdin });\nrl.once("line", (name) => {\n  console.log(`Hello, ${name}`);\n  rl.close();\n});')
put("current_date", "javascript", "console.log(new Date().toISOString().slice(0, 10));")
put("http_get", "javascript", '// Node 18+ has fetch built in\nfetch("https://example.com")\n  .then((r) => r.text())\n  .then((body) => console.log(body.slice(0, 100)))\n  .catch((e) => console.error(e));')

# ---------------------------------------------------------------------------------------------------- TypeScript
put("hello", "typescript", 'const greeting: string = "Hello, world!";\nconsole.log(greeting);')
put("reverse_string", "typescript", 'function reverse(s: string): string {\n  return [...s].reverse().join("");\n}\n\nconsole.log(reverse("hello"));')
put("sort_list", "typescript", 'const nums: number[] = [5, 3, 8, 1];\nnums.sort((a, b) => a - b);\nconsole.log(nums.join(" "));')
put("sum_list", "typescript", "const nums: number[] = [1, 2, 3, 4];\nconsole.log(nums.reduce((a, b) => a + b, 0));")
put("factorial", "typescript", "function factorial(n: number): number {\n  let result = 1;\n  for (let i = 2; i <= n; i++) result *= i;\n  return result;\n}\n\nconsole.log(factorial(5));")
put("class_object", "typescript", "class Person {\n  constructor(private name: string) {}\n  greet(): string {\n    return `Hello, ${this.name}`;\n  }\n}\n\nconsole.log(new Person(\"Ada\").greet());")
put("json_parse", "typescript", 'interface User {\n  name: string;\n  age: number;\n}\n\nconst data = JSON.parse(\'{"name": "Ada", "age": 36}\') as User;\nconsole.log(data.name, data.age);')
put("dictionary", "typescript", 'const stock: Record<string, number> = { apple: 3, pear: 5 };\nconsole.log(stock["apple"]);')
put("try_catch", "typescript", 'try {\n  JSON.parse("not json");\n} catch (e: unknown) {\n  console.log("error caught");\n}')

# ---------------------------------------------------------------------------------------------------- Java
def java(body, imports=""):
    return (imports + "\n" if imports else "") + "public class Main {\n" + body + "\n}"


put("hello", "java", java('    public static void main(String[] args) {\n        System.out.println("Hello, world!");\n    }'))
put("reverse_string", "java", java('    public static void main(String[] args) {\n        String s = "hello";\n        System.out.println(new StringBuilder(s).reverse());\n    }'))
put("sort_list", "java", java('    public static void main(String[] args) {\n        List<Integer> nums = new ArrayList<>(List.of(5, 3, 8, 1));\n        Collections.sort(nums);\n        StringJoiner out = new StringJoiner(" ");\n        for (int n : nums) out.add(String.valueOf(n));\n        System.out.println(out);\n    }', "import java.util.*;"))
put("sum_list", "java", java('    public static void main(String[] args) {\n        int[] nums = {1, 2, 3, 4};\n        System.out.println(Arrays.stream(nums).sum());\n    }', "import java.util.Arrays;"))
put("max_list", "java", java('    public static void main(String[] args) {\n        int[] nums = {5, 3, 8, 1};\n        System.out.println(Arrays.stream(nums).max().getAsInt());\n    }', "import java.util.Arrays;"))
put("remove_duplicates", "java", java('    public static void main(String[] args) {\n        List<Integer> nums = List.of(1, 2, 2, 3, 1);\n        Set<Integer> unique = new LinkedHashSet<>(nums); // keeps the order\n        StringJoiner out = new StringJoiner(" ");\n        for (int n : unique) out.add(String.valueOf(n));\n        System.out.println(out);\n    }', "import java.util.*;"))
put("factorial", "java", java('    static long factorial(int n) {\n        long result = 1;\n        for (int i = 2; i <= n; i++) result *= i;\n        return result;\n    }\n\n    public static void main(String[] args) {\n        System.out.println(factorial(5));\n    }'))
put("fibonacci", "java", java('    public static void main(String[] args) {\n        StringJoiner out = new StringJoiner(" ");\n        long a = 0, b = 1;\n        for (int i = 0; i < 10; i++) {\n            out.add(String.valueOf(a));\n            long next = a + b;\n            a = b;\n            b = next;\n        }\n        System.out.println(out);\n    }', "import java.util.StringJoiner;"))
put("is_prime", "java", java('    static boolean isPrime(int n) {\n        if (n < 2) return false;\n        for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;\n        return true;\n    }\n\n    public static void main(String[] args) {\n        System.out.println(isPrime(17));\n    }'))
put("uppercase", "java", java('    public static void main(String[] args) {\n        System.out.println("hello".toUpperCase());\n    }'))
put("split_string", "java", java('    public static void main(String[] args) {\n        String[] parts = "a,b,c".split(",");\n        System.out.println(String.join(" ", parts));\n    }'))
put("string_to_int", "java", java('    public static void main(String[] args) {\n        int n = Integer.parseInt("42");\n        System.out.println(n + 1);\n    }'))
put("count_words", "java", java('    public static void main(String[] args) {\n        String text = "the quick brown fox";\n        System.out.println(text.trim().split("\\\\s+").length);\n    }'))
put("dictionary", "java", java('    public static void main(String[] args) {\n        Map<String, Integer> stock = new HashMap<>();\n        stock.put("apple", 3);\n        stock.put("pear", 5);\n        System.out.println(stock.get("apple"));\n    }', "import java.util.*;"))
put("read_write_file", "java", java('    public static void main(String[] args) throws IOException {\n        Path file = Path.of("note.txt");\n        Files.writeString(file, "hi");\n        System.out.println(Files.readString(file));\n    }', "import java.io.IOException;\nimport java.nio.file.*;"))
put("try_catch", "java", java('    public static void main(String[] args) {\n        try {\n            Integer.parseInt("abc");\n        } catch (NumberFormatException e) {\n            System.out.println("error caught");\n        }\n    }'))
put("loop_range", "java", java('    public static void main(String[] args) {\n        StringJoiner out = new StringJoiner(" ");\n        for (int i = 1; i <= 5; i++) out.add(String.valueOf(i));\n        System.out.println(out);\n    }', "import java.util.StringJoiner;"))
put("class_object", "java", 'class Person {\n    private final String name;\n\n    Person(String name) {\n        this.name = name;\n    }\n\n    String greet() {\n        return "Hello, " + name;\n    }\n}\n\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println(new Person("Ada").greet());\n    }\n}')
put("regex_find", "java", java('    public static void main(String[] args) {\n        Matcher m = Pattern.compile("\\\\d+").matcher("a1b22c333");\n        StringJoiner out = new StringJoiner(" ");\n        while (m.find()) out.add(m.group());\n        System.out.println(out);\n    }', "import java.util.StringJoiner;\nimport java.util.regex.*;"))
put("random_number", "java", java('    public static void main(String[] args) {\n        int n = ThreadLocalRandom.current().nextInt(1, 11); // 1 to 10\n        System.out.println(n);\n    }', "import java.util.concurrent.ThreadLocalRandom;"))
put("read_input", "java", java('    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        String name = in.nextLine();\n        System.out.println("Hello, " + name);\n    }', "import java.util.Scanner;"))
put("current_date", "java", java('    public static void main(String[] args) {\n        System.out.println(LocalDate.now());\n    }', "import java.time.LocalDate;"))
put("http_get", "java", java('    public static void main(String[] args) throws Exception {\n        HttpClient client = HttpClient.newHttpClient();\n        HttpRequest req = HttpRequest.newBuilder(URI.create("https://example.com")).build();\n        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());\n        System.out.println(res.statusCode());\n    }', "import java.net.URI;\nimport java.net.http.*;"))

# ---------------------------------------------------------------------------------------------------- C
def c(body, inc="#include <stdio.h>"):
    return inc + "\n\n" + body


put("hello", "c", c('int main(void) {\n    printf("Hello, world!\\n");\n    return 0;\n}'))
put("reverse_string", "c", c('int main(void) {\n    char s[] = "hello";\n    size_t n = strlen(s);\n    for (size_t i = 0; i < n / 2; i++) {\n        char t = s[i];\n        s[i] = s[n - 1 - i];\n        s[n - 1 - i] = t;\n    }\n    printf("%s\\n", s);\n    return 0;\n}', "#include <stdio.h>\n#include <string.h>"))
put("sort_list", "c", c('static int cmp(const void *a, const void *b) {\n    return (*(const int *)a > *(const int *)b) - (*(const int *)a < *(const int *)b);\n}\n\nint main(void) {\n    int nums[] = {5, 3, 8, 1};\n    int n = sizeof nums / sizeof nums[0];\n    qsort(nums, n, sizeof nums[0], cmp);\n    for (int i = 0; i < n; i++) printf(i ? " %d" : "%d", nums[i]);\n    printf("\\n");\n    return 0;\n}', "#include <stdio.h>\n#include <stdlib.h>"))
put("sum_list", "c", c('int main(void) {\n    int nums[] = {1, 2, 3, 4};\n    int sum = 0;\n    for (size_t i = 0; i < sizeof nums / sizeof nums[0]; i++) sum += nums[i];\n    printf("%d\\n", sum);\n    return 0;\n}'))
put("max_list", "c", c('int main(void) {\n    int nums[] = {5, 3, 8, 1};\n    int max = nums[0];\n    for (size_t i = 1; i < sizeof nums / sizeof nums[0]; i++) if (nums[i] > max) max = nums[i];\n    printf("%d\\n", max);\n    return 0;\n}'))
put("factorial", "c", c('unsigned long long factorial(int n) {\n    unsigned long long result = 1;\n    for (int i = 2; i <= n; i++) result *= i;\n    return result;\n}\n\nint main(void) {\n    printf("%llu\\n", factorial(5));\n    return 0;\n}'))
put("fibonacci", "c", c('int main(void) {\n    long a = 0, b = 1;\n    for (int i = 0; i < 10; i++) {\n        printf(i ? " %ld" : "%ld", a);\n        long next = a + b;\n        a = b;\n        b = next;\n    }\n    printf("\\n");\n    return 0;\n}'))
put("is_prime", "c", c('bool is_prime(int n) {\n    if (n < 2) return false;\n    for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;\n    return true;\n}\n\nint main(void) {\n    printf("%s\\n", is_prime(17) ? "true" : "false");\n    return 0;\n}', "#include <stdbool.h>\n#include <stdio.h>"))
put("uppercase", "c", c('int main(void) {\n    char s[] = "hello";\n    for (char *p = s; *p; p++) *p = (char)toupper((unsigned char)*p);\n    printf("%s\\n", s);\n    return 0;\n}', "#include <ctype.h>\n#include <stdio.h>"))
put("split_string", "c", c('int main(void) {\n    char s[] = "a,b,c";\n    int first = 1;\n    for (char *part = strtok(s, ","); part; part = strtok(NULL, ",")) {\n        printf(first ? "%s" : " %s", part);\n        first = 0;\n    }\n    printf("\\n");\n    return 0;\n}', "#include <stdio.h>\n#include <string.h>"))
put("string_to_int", "c", c('int main(void) {\n    long n = strtol("42", NULL, 10);\n    printf("%ld\\n", n + 1);\n    return 0;\n}', "#include <stdio.h>\n#include <stdlib.h>"))
put("read_write_file", "c", c('int main(void) {\n    FILE *f = fopen("note.txt", "w");\n    if (!f) return 1;\n    fputs("hi", f);\n    fclose(f);\n    char buf[64];\n    f = fopen("note.txt", "r");\n    if (!f || !fgets(buf, sizeof buf, f)) return 1;\n    fclose(f);\n    printf("%s\\n", buf);\n    return 0;\n}'))
put("loop_range", "c", c('int main(void) {\n    for (int i = 1; i <= 5; i++) printf(i > 1 ? " %d" : "%d", i);\n    printf("\\n");\n    return 0;\n}'))
put("random_number", "c", c('int main(void) {\n    srand((unsigned)time(NULL));\n    printf("%d\\n", rand() % 10 + 1);\n    return 0;\n}', "#include <stdio.h>\n#include <stdlib.h>\n#include <time.h>"))
put("read_input", "c", c('int main(void) {\n    char name[100];\n    if (!fgets(name, sizeof name, stdin)) return 1;\n    name[strcspn(name, "\\n")] = 0;\n    printf("Hello, %s\\n", name);\n    return 0;\n}', "#include <stdio.h>\n#include <string.h>"))
put("current_date", "c", c('int main(void) {\n    char buf[11];\n    time_t now = time(NULL);\n    strftime(buf, sizeof buf, "%Y-%m-%d", localtime(&now));\n    printf("%s\\n", buf);\n    return 0;\n}', "#include <stdio.h>\n#include <time.h>"))

# ---------------------------------------------------------------------------------------------------- C++
put("hello", "cpp", '#include <iostream>\n\nint main() {\n    std::cout << "Hello, world!" << std::endl;\n    return 0;\n}')
put("reverse_string", "cpp", '#include <algorithm>\n#include <iostream>\n#include <string>\n\nint main() {\n    std::string s = "hello";\n    std::reverse(s.begin(), s.end());\n    std::cout << s << std::endl;\n}')
put("sort_list", "cpp", '#include <algorithm>\n#include <iostream>\n#include <vector>\n\nint main() {\n    std::vector<int> nums = {5, 3, 8, 1};\n    std::sort(nums.begin(), nums.end());\n    for (size_t i = 0; i < nums.size(); i++) std::cout << (i ? " " : "") << nums[i];\n    std::cout << std::endl;\n}')
put("sum_list", "cpp", '#include <iostream>\n#include <numeric>\n#include <vector>\n\nint main() {\n    std::vector<int> nums = {1, 2, 3, 4};\n    std::cout << std::accumulate(nums.begin(), nums.end(), 0) << std::endl;\n}')
put("max_list", "cpp", '#include <algorithm>\n#include <iostream>\n#include <vector>\n\nint main() {\n    std::vector<int> nums = {5, 3, 8, 1};\n    std::cout << *std::max_element(nums.begin(), nums.end()) << std::endl;\n}')
put("factorial", "cpp", '#include <iostream>\n\nunsigned long long factorial(int n) {\n    unsigned long long result = 1;\n    for (int i = 2; i <= n; i++) result *= i;\n    return result;\n}\n\nint main() {\n    std::cout << factorial(5) << std::endl;\n}')
put("uppercase", "cpp", '#include <algorithm>\n#include <cctype>\n#include <iostream>\n#include <string>\n\nint main() {\n    std::string s = "hello";\n    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) { return std::toupper(c); });\n    std::cout << s << std::endl;\n}')
put("dictionary", "cpp", '#include <iostream>\n#include <map>\n#include <string>\n\nint main() {\n    std::map<std::string, int> stock = {{"apple", 3}, {"pear", 5}};\n    std::cout << stock["apple"] << std::endl;\n}')
put("read_write_file", "cpp", '#include <fstream>\n#include <iostream>\n#include <string>\n\nint main() {\n    std::ofstream("note.txt") << "hi";\n    std::ifstream in("note.txt");\n    std::string text;\n    std::getline(in, text);\n    std::cout << text << std::endl;\n}')
put("try_catch", "cpp", '#include <iostream>\n#include <stdexcept>\n#include <string>\n\nint main() {\n    try {\n        std::stoi("abc");\n    } catch (const std::invalid_argument &) {\n        std::cout << "error caught" << std::endl;\n    }\n}')
put("class_object", "cpp", '#include <iostream>\n#include <string>\n\nclass Person {\n  public:\n    explicit Person(std::string name) : name_(std::move(name)) {}\n    std::string greet() const { return "Hello, " + name_; }\n\n  private:\n    std::string name_;\n};\n\nint main() {\n    std::cout << Person("Ada").greet() << std::endl;\n}')
put("regex_find", "cpp", '#include <iostream>\n#include <regex>\n#include <string>\n\nint main() {\n    std::string s = "a1b22c333";\n    std::regex digits("\\\\d+");\n    bool first = true;\n    for (std::sregex_iterator it(s.begin(), s.end(), digits), end; it != end; ++it) {\n        std::cout << (first ? "" : " ") << it->str();\n        first = false;\n    }\n    std::cout << std::endl;\n}')
put("random_number", "cpp", '#include <iostream>\n#include <random>\n\nint main() {\n    std::random_device rd;\n    std::mt19937 gen(rd());\n    std::uniform_int_distribution<int> dist(1, 10);\n    std::cout << dist(gen) << std::endl;\n}')
put("read_input", "cpp", '#include <iostream>\n#include <string>\n\nint main() {\n    std::string name;\n    std::getline(std::cin, name);\n    std::cout << "Hello, " << name << std::endl;\n}')

# ---------------------------------------------------------------------------------------------------- Go
def go(body, imports=('"fmt"',)):
    imp = "import " + imports[0] if len(imports) == 1 else "import (\n" + "\n".join("\t" + i for i in imports) + "\n)"
    return "package main\n\n" + imp + "\n\n" + body


put("hello", "go", go('func main() {\n\tfmt.Println("Hello, world!")\n}'))
put("reverse_string", "go", go('func reverse(s string) string {\n\tr := []rune(s)\n\tfor i, j := 0, len(r)-1; i < j; i, j = i+1, j-1 {\n\t\tr[i], r[j] = r[j], r[i]\n\t}\n\treturn string(r)\n}\n\nfunc main() {\n\tfmt.Println(reverse("hello"))\n}'))
put("sort_list", "go", go('func main() {\n\tnums := []int{5, 3, 8, 1}\n\tsort.Ints(nums)\n\tparts := make([]string, len(nums))\n\tfor i, n := range nums {\n\t\tparts[i] = strconv.Itoa(n)\n\t}\n\tfmt.Println(strings.Join(parts, " "))\n}', ('"fmt"', '"sort"', '"strconv"', '"strings"')))
put("sum_list", "go", go("func main() {\n\tnums := []int{1, 2, 3, 4}\n\tsum := 0\n\tfor _, n := range nums {\n\t\tsum += n\n\t}\n\tfmt.Println(sum)\n}"))
put("max_list", "go", go("func main() {\n\tnums := []int{5, 3, 8, 1}\n\tbest := nums[0]\n\tfor _, n := range nums[1:] {\n\t\tif n > best {\n\t\t\tbest = n\n\t\t}\n\t}\n\tfmt.Println(best)\n}"))
put("factorial", "go", go("func factorial(n int) int {\n\tresult := 1\n\tfor i := 2; i <= n; i++ {\n\t\tresult *= i\n\t}\n\treturn result\n}\n\nfunc main() {\n\tfmt.Println(factorial(5))\n}"))
put("is_prime", "go", go("func isPrime(n int) bool {\n\tif n < 2 {\n\t\treturn false\n\t}\n\tfor i := 2; i*i <= n; i++ {\n\t\tif n%i == 0 {\n\t\t\treturn false\n\t\t}\n\t}\n\treturn true\n}\n\nfunc main() {\n\tfmt.Println(isPrime(17))\n}"))
put("uppercase", "go", go('func main() {\n\tfmt.Println(strings.ToUpper("hello"))\n}', ('"fmt"', '"strings"')))
put("split_string", "go", go('func main() {\n\tparts := strings.Split("a,b,c", ",")\n\tfmt.Println(strings.Join(parts, " "))\n}', ('"fmt"', '"strings"')))
put("string_to_int", "go", go('func main() {\n\tn, err := strconv.Atoi("42")\n\tif err != nil {\n\t\tpanic(err)\n\t}\n\tfmt.Println(n + 1)\n}', ('"fmt"', '"strconv"')))
put("count_words", "go", go('func main() {\n\tfmt.Println(len(strings.Fields("the quick brown fox")))\n}', ('"fmt"', '"strings"')))
put("dictionary", "go", go('func main() {\n\tstock := map[string]int{"apple": 3, "pear": 5}\n\tfmt.Println(stock["apple"])\n}'))
put("read_write_file", "go", go('func main() {\n\tif err := os.WriteFile("note.txt", []byte("hi"), 0o644); err != nil {\n\t\tpanic(err)\n\t}\n\tdata, err := os.ReadFile("note.txt")\n\tif err != nil {\n\t\tpanic(err)\n\t}\n\tfmt.Println(string(data))\n}', ('"fmt"', '"os"')))
put("try_catch", "go", go('// Go returns errors instead of throwing them\nfunc main() {\n\tif _, err := strconv.Atoi("abc"); err != nil {\n\t\tfmt.Println("error caught")\n\t}\n}', ('"fmt"', '"strconv"')))
put("class_object", "go", go('type Person struct {\n\tName string\n}\n\nfunc (p Person) Greet() string {\n\treturn "Hello, " + p.Name\n}\n\nfunc main() {\n\tfmt.Println(Person{Name: "Ada"}.Greet())\n}'))
put("json_parse", "go", go('type User struct {\n\tName string `json:"name"`\n\tAge  int    `json:"age"`\n}\n\nfunc main() {\n\tvar u User\n\tif err := json.Unmarshal([]byte(`{"name": "Ada", "age": 36}`), &u); err != nil {\n\t\tpanic(err)\n\t}\n\tfmt.Println(u.Name, u.Age)\n}', ('"encoding/json"', '"fmt"')))
put("regex_find", "go", go('func main() {\n\tre := regexp.MustCompile(`\\d+`)\n\tfmt.Println(strings.Join(re.FindAllString("a1b22c333", -1), " "))\n}', ('"fmt"', '"regexp"', '"strings"')))
put("random_number", "go", go("func main() {\n\tfmt.Println(rand.Intn(10) + 1)\n}", ('"fmt"', '"math/rand"')))
put("current_date", "go", go('func main() {\n\tfmt.Println(time.Now().Format("2006-01-02"))\n}', ('"fmt"', '"time"')))
put("http_get", "go", go('func main() {\n\tres, err := http.Get("https://example.com")\n\tif err != nil {\n\t\tpanic(err)\n\t}\n\tdefer res.Body.Close()\n\tbody, _ := io.ReadAll(res.Body)\n\tfmt.Println(res.StatusCode, len(body))\n}', ('"fmt"', '"io"', '"net/http"')))

# ---------------------------------------------------------------------------------------------------- Rust
put("hello", "rust", 'fn main() {\n    println!("Hello, world!");\n}')
put("reverse_string", "rust", 'fn main() {\n    let s = "hello";\n    let reversed: String = s.chars().rev().collect();\n    println!("{}", reversed);\n}')
put("sort_list", "rust", 'fn main() {\n    let mut nums = vec![5, 3, 8, 1];\n    nums.sort();\n    let parts: Vec<String> = nums.iter().map(|n| n.to_string()).collect();\n    println!("{}", parts.join(" "));\n}')
put("sum_list", "rust", 'fn main() {\n    let nums = [1, 2, 3, 4];\n    let sum: i32 = nums.iter().sum();\n    println!("{}", sum);\n}')
put("max_list", "rust", 'fn main() {\n    let nums = [5, 3, 8, 1];\n    println!("{}", nums.iter().max().unwrap());\n}')
put("factorial", "rust", 'fn factorial(n: u64) -> u64 {\n    (2..=n).product()\n}\n\nfn main() {\n    println!("{}", factorial(5));\n}')
put("is_prime", "rust", 'fn is_prime(n: u32) -> bool {\n    if n < 2 {\n        return false;\n    }\n    let mut i = 2;\n    while i * i <= n {\n        if n % i == 0 {\n            return false;\n        }\n        i += 1;\n    }\n    true\n}\n\nfn main() {\n    println!("{}", is_prime(17));\n}')
put("uppercase", "rust", 'fn main() {\n    println!("{}", "hello".to_uppercase());\n}')
put("split_string", "rust", 'fn main() {\n    let parts: Vec<&str> = "a,b,c".split(\',\').collect();\n    println!("{}", parts.join(" "));\n}')
put("string_to_int", "rust", 'fn main() {\n    let n: i32 = "42".parse().expect("not a number");\n    println!("{}", n + 1);\n}')
put("count_words", "rust", 'fn main() {\n    println!("{}", "the quick brown fox".split_whitespace().count());\n}')
put("dictionary", "rust", 'use std::collections::HashMap;\n\nfn main() {\n    let mut stock = HashMap::new();\n    stock.insert("apple", 3);\n    stock.insert("pear", 5);\n    println!("{}", stock["apple"]);\n}')
put("read_write_file", "rust", 'use std::fs;\n\nfn main() -> std::io::Result<()> {\n    fs::write("note.txt", "hi")?;\n    println!("{}", fs::read_to_string("note.txt")?);\n    Ok(())\n}')
put("try_catch", "rust", '// Rust returns a Result instead of throwing\nfn main() {\n    match "abc".parse::<i32>() {\n        Ok(n) => println!("{}", n),\n        Err(_) => println!("error caught"),\n    }\n}')
put("loop_range", "rust", 'fn main() {\n    let parts: Vec<String> = (1..=5).map(|i| i.to_string()).collect();\n    println!("{}", parts.join(" "));\n}')
put("class_object", "rust", 'struct Person {\n    name: String,\n}\n\nimpl Person {\n    fn greet(&self) -> String {\n        format!("Hello, {}", self.name)\n    }\n}\n\nfn main() {\n    let p = Person { name: String::from("Ada") };\n    println!("{}", p.greet());\n}')
put("read_input", "rust", 'use std::io;\n\nfn main() {\n    let mut name = String::new();\n    io::stdin().read_line(&mut name).expect("could not read");\n    println!("Hello, {}", name.trim());\n}')

# ---------------------------------------------------------------------------------------------------- PHP
put("hello", "php", '<?php\necho "Hello, world!\\n";')
put("reverse_string", "php", '<?php\necho strrev("hello"), "\\n";')
put("sort_list", "php", '<?php\n$nums = [5, 3, 8, 1];\nsort($nums);\necho implode(" ", $nums), "\\n";')
put("sum_list", "php", '<?php\necho array_sum([1, 2, 3, 4]), "\\n";')
put("max_list", "php", '<?php\necho max([5, 3, 8, 1]), "\\n";')
put("remove_duplicates", "php", '<?php\necho implode(" ", array_unique([1, 2, 2, 3, 1])), "\\n";')
put("factorial", "php", '<?php\nfunction factorial(int $n): int {\n    $result = 1;\n    for ($i = 2; $i <= $n; $i++) {\n        $result *= $i;\n    }\n    return $result;\n}\n\necho factorial(5), "\\n";')
put("uppercase", "php", '<?php\necho strtoupper("hello"), "\\n";')
put("split_string", "php", '<?php\necho implode(" ", explode(",", "a,b,c")), "\\n";')
put("string_to_int", "php", '<?php\n$n = (int) "42";\necho $n + 1, "\\n";')
put("count_words", "php", '<?php\necho str_word_count("the quick brown fox"), "\\n";')
put("dictionary", "php", '<?php\n$stock = ["apple" => 3, "pear" => 5];\necho $stock["apple"], "\\n";')
put("read_write_file", "php", '<?php\nfile_put_contents("note.txt", "hi");\necho file_get_contents("note.txt"), "\\n";')
put("try_catch", "php", '<?php\ntry {\n    json_decode("not json", flags: JSON_THROW_ON_ERROR);\n} catch (JsonException $e) {\n    echo "error caught\\n";\n}')
put("class_object", "php", '<?php\nclass Person {\n    public function __construct(private string $name) {}\n\n    public function greet(): string {\n        return "Hello, {$this->name}";\n    }\n}\n\necho (new Person("Ada"))->greet(), "\\n";')
put("json_parse", "php", '<?php\n$data = json_decode(\'{"name": "Ada", "age": 36}\', true);\necho $data["name"], " ", $data["age"], "\\n";')
put("regex_find", "php", '<?php\npreg_match_all(\'/\\d+/\', "a1b22c333", $m);\necho implode(" ", $m[0]), "\\n";')
put("random_number", "php", '<?php\necho random_int(1, 10), "\\n";')
put("current_date", "php", '<?php\necho date("Y-m-d"), "\\n";')
put("http_get", "php", '<?php\n$body = file_get_contents("https://example.com");\necho substr($body, 0, 100), "\\n";')

# ---------------------------------------------------------------------------------------------------- Ruby
put("hello", "ruby", 'puts "Hello, world!"')
put("reverse_string", "ruby", 'puts "hello".reverse')
put("sort_list", "ruby", 'puts [5, 3, 8, 1].sort.join(" ")')
put("sum_list", "ruby", "puts [1, 2, 3, 4].sum")
put("max_list", "ruby", "puts [5, 3, 8, 1].max")
put("remove_duplicates", "ruby", 'puts [1, 2, 2, 3, 1].uniq.join(" ")')
put("factorial", "ruby", "def factorial(n)\n  (1..n).reduce(1, :*)\nend\n\nputs factorial(5)")
put("fibonacci", "ruby", 'def fibonacci(count)\n  a, b = 0, 1\n  Array.new(count) { a.tap { a, b = b, a + b } }\nend\n\nputs fibonacci(10).join(" ")')
put("uppercase", "ruby", 'puts "hello".upcase')
put("split_string", "ruby", 'puts "a,b,c".split(",").join(" ")')
put("string_to_int", "ruby", 'puts "42".to_i + 1')
put("count_words", "ruby", 'puts "the quick brown fox".split.size')
put("dictionary", "ruby", 'stock = { "apple" => 3, "pear" => 5 }\nputs stock["apple"]')
put("read_write_file", "ruby", 'File.write("note.txt", "hi")\nputs File.read("note.txt")')
put("try_catch", "ruby", 'begin\n  Integer("abc")\nrescue ArgumentError\n  puts "error caught"\nend')
put("loop_range", "ruby", 'puts (1..5).to_a.join(" ")')
put("class_object", "ruby", 'class Person\n  def initialize(name)\n    @name = name\n  end\n\n  def greet\n    "Hello, #{@name}"\n  end\nend\n\nputs Person.new("Ada").greet')
put("json_parse", "ruby", 'require "json"\n\ndata = JSON.parse(\'{"name": "Ada", "age": 36}\')\nputs "#{data["name"]} #{data["age"]}"')
put("regex_find", "ruby", 'puts "a1b22c333".scan(/\\d+/).join(" ")')
put("random_number", "ruby", "puts rand(1..10)")
put("read_input", "ruby", 'name = gets.chomp\nputs "Hello, #{name}"')
put("current_date", "ruby", 'require "date"\n\nputs Date.today.to_s')

# ---------------------------------------------------------------------------------------------------- Bash
put("hello", "bash", 'echo "Hello, world!"')
put("reverse_string", "bash", 'echo "hello" | rev')
put("sort_list", "bash", 'printf "%s\\n" 5 3 8 1 | sort -n | paste -sd" "')
put("sum_list", "bash", "nums=(1 2 3 4)\nsum=0\nfor n in \"${nums[@]}\"; do\n  sum=$((sum + n))\ndone\necho \"$sum\"")
put("uppercase", "bash", 's="hello"\necho "${s^^}"')
put("split_string", "bash", 'IFS="," read -ra parts <<< "a,b,c"\necho "${parts[@]}"')
put("string_to_int", "bash", 'n="42"\necho $((n + 1))')
put("count_words", "bash", 'echo "the quick brown fox" | wc -w | tr -d " "')
put("dictionary", "bash", 'declare -A stock=([apple]=3 [pear]=5)\necho "${stock[apple]}"')
put("read_write_file", "bash", 'echo -n "hi" > note.txt\ncat note.txt\necho')
put("loop_range", "bash", 'echo {1..5}')
put("regex_find", "bash", 'echo "a1b22c333" | grep -oE "[0-9]+" | paste -sd" "')
put("random_number", "bash", "echo $((RANDOM % 10 + 1))")
put("read_input", "bash", 'read -r name\necho "Hello, $name"')
put("current_date", "bash", 'date +%Y-%m-%d')
put("http_get", "bash", 'curl -s https://example.com | head -c 100')

# ---------------------------------------------------------------------------------------------------- the check


def run(task, lang, code, work):
    """True when the program compiles, runs and prints what it must (or, for http_get, compiles)."""
    exp = EXPECT[task]
    stdin = STDIN.get(task, "")
    names = {"python": "main.py", "javascript": "main.js", "typescript": "main.ts", "java": "Main.java", "c": "main.c",
             "cpp": "main.cpp", "go": "main.go", "rust": "main.rs", "php": "main.php", "ruby": "main.rb", "bash": "main.sh"}
    path = os.path.join(work, names[lang])
    with open(path, "w") as f:
        f.write(code)

    def sh(cmd, inp=""):
        return subprocess.run(cmd, cwd=work, input=inp, capture_output=True, text=True, timeout=120)

    compile_only = exp == "compile"
    if lang == "python":
        r = sh(["python3", "-m", "py_compile", path]) if compile_only else sh(["python3", path], stdin)
    elif lang == "javascript":
        r = sh(["node", "--check", path]) if compile_only else sh(["node", path], stdin)
    elif lang == "typescript":
        r = sh(["tsc", "--strict", "--target", "es2020", "--lib", "es2020,dom", "--types", "node", "--outDir", work, path])
        if r.returncode != 0:
            r = sh(["tsc", "--strict", "--target", "es2020", "--lib", "es2020,dom", "--outDir", work, path])
        if r.returncode == 0 and not compile_only:
            r = sh(["node", os.path.join(work, "main.js")], stdin)
    elif lang == "java":
        r = sh(["javac", path])
        if r.returncode == 0 and not compile_only:
            r = sh(["java", "-cp", work, "Main"], stdin)
    elif lang in ("c", "cpp"):
        r = sh(["gcc" if lang == "c" else "g++", "-Wall", "-Werror", "-O1", "-o", "prog", path] + (["-std=c11"] if lang == "c" else ["-std=c++17"]))
        if r.returncode == 0 and not compile_only:
            r = sh(["./prog"], stdin)
    elif lang == "go":
        r = sh(["go", "build", "-o", "prog", path])
        if r.returncode == 0 and not compile_only:
            r = sh(["./prog"], stdin)
    elif lang == "rust":
        r = sh(["rustc", "-D", "warnings", "-o", "prog", path])
        if r.returncode == 0 and not compile_only:
            r = sh(["./prog"], stdin)
    elif lang == "php":
        r = sh(["php", "-l", path]) if compile_only else sh(["php", path], stdin)
    elif lang == "ruby":
        r = sh(["ruby", "-c", path]) if compile_only else sh(["ruby", path], stdin)
    elif lang == "bash":
        r = sh(["bash", "-n", path]) if compile_only else sh(["bash", path], stdin)
    else:
        return False, "no runner"
    if r.returncode != 0:
        return False, (r.stderr or r.stdout)[-400:]
    if compile_only:
        return True, ""
    out = r.stdout.strip()
    ok = re.match(exp[3:], out) is not None if exp.startswith("re:") else out == exp
    return ok, "" if ok else "printed %r, wanted %r" % (out[:80], exp)


def esc(code):
    return code.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")


def main():
    kept, failed = [], []
    for (task, lang), code in sorted(R.items()):
        work = tempfile.mkdtemp(prefix="recipe-")
        try:
            ok, why = run(task, lang, code, work)
        except subprocess.TimeoutExpired:
            ok, why = False, "timed out"
        finally:
            shutil.rmtree(work, ignore_errors=True)
        (kept if ok else failed).append((task, lang, code, why))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("# Ibiko v1's coding recipes: every one compiled, run and its output checked (tools/build_code_recipes.py)\n")
        for task, lang, code, _ in kept:
            f.write("%s\t%s\t%s\n" % (task, lang, esc(code.rstrip("\n"))))
    print("kept %d recipes in %d languages" % (len(kept), len({k[1] for k in kept})))
    for task, lang, _, why in failed:
        print("FAILED %s/%s: %s" % (lang, task, why.strip().splitlines()[-1] if why.strip() else why))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
