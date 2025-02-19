package com.example.mdpcontroller.tab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import com.example.mdpcontroller.R;

public class ChatTabFragment extends Fragment {

    private EditText chatEditText;
    private Button buttonConnect;

    public ChatTabFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_chat_tab, container, false);

        // Initialize chatEditText and buttonConnect
        chatEditText = view.findViewById(R.id.chatEditText);
        buttonConnect = getActivity().findViewById(R.id.button_connect);  // Reference to button in activity

        // Set focus change listener on chatEditText
        chatEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                buttonConnect.setVisibility(View.GONE);  // Hide button when EditText is clicked
            } else {
                buttonConnect.setVisibility(View.VISIBLE);  // Show button when EditText loses focus
            }
        });

        return view;
    }
}