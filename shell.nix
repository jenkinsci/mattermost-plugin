{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = [
    pkgs.jdk25
    (pkgs.maven.override { jdk_headless = pkgs.jdk25; })
    pkgs.git
  ];

  JAVA_HOME = pkgs.jdk25.home;
  FONTCONFIG_FILE = pkgs.makeFontsConf {
    fontDirectories = [ pkgs.dejavu_fonts ];
  };
}
