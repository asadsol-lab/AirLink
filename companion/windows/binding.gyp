{
  "targets": [
    {
      "target_name": "airlink_win32_input",
      "sources": [
        "addon.cpp",
        "input_injector.cpp"
      ],
      "conditions": [
        [
          "OS=='win'",
          {
            "libraries": [
              "-luser32.lib"
            ]
          }
        ]
      ]
    }
  ]
}
