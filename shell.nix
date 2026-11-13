{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = [
    pkgs.jdk21
    (pkgs.maven.override { jdk_headless = pkgs.jdk21; })
    pkgs.git
  ];

  JAVA_HOME = pkgs.jdk21.home;
  # Maven's archive plugins require ZIP timestamps to start at 1980-01-01T00:00:02Z.
  SOURCE_DATE_EPOCH = "315532802";
  FONTCONFIG_FILE = pkgs.makeFontsConf {
    fontDirectories = [ pkgs.dejavu_fonts ];
  };
}
