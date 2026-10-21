import { describe, expect, it } from "vitest";
import { PATHNAME_HEADER } from "@/lib/request-headers";

describe("request-headers", () => {
  it("defines PATHNAME_HEADER as x-pathname", () => {
    expect(PATHNAME_HEADER).toBe("x-pathname");
  });
});
